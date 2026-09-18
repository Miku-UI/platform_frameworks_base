/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use anyhow::{bail, Result};
use atrace::AtraceTag;
use native_activity_thread_bindgen::ashmem_get_size_region;
use nix::sys::mman;
use std::{
    convert::TryFrom,
    os::fd::{AsRawFd, OwnedFd},
    ptr::NonNull,
    sync::OnceLock,
};

static DEFAULT_FONT_FAMILY: &str = "sans-serif";

struct Ashmem {
    ptr: NonNull<libc::c_void>,
    size: usize,
}

// SAFETY: `self.ptr` is mmap'ed and is valid across the entire process address space.
unsafe impl Send for Ashmem {}

impl TryFrom<OwnedFd> for Ashmem {
    type Error = anyhow::Error;

    fn try_from(fd: OwnedFd) -> Result<Self> {
        // SAFETY: The passed fd is valid.
        let size = unsafe { ashmem_get_size_region(fd.as_raw_fd()) };
        if size < 0 {
            bail!("Failed to get the size of system font map ashmem region")
        }
        let size = size as usize;
        // SAFETY: The passed fd and length are valid.
        let ptr = unsafe {
            mman::mmap(
                None,
                size.try_into().unwrap(),
                mman::ProtFlags::PROT_READ,
                mman::MapFlags::MAP_SHARED,
                fd,
                0,
            )?
        };
        Ok(Self { ptr, size })
    }
}

impl Drop for Ashmem {
    fn drop(&mut self) {
        // SAFETY: `self.ptr` points to memory returned from `mmap` and `self.size` is the length of
        // the mapped memory.
        let _ = unsafe { mman::munmap(self.ptr, self.size) };
    }
}

impl AsRef<[u8]> for Ashmem {
    fn as_ref(&self) -> &[u8] {
        // SAFETY: `self.ptr` is returned from mmap(2) thus addr points to `self.size` consecutive
        // bytes of valid memory.
        unsafe { std::slice::from_raw_parts(self.ptr.as_ptr() as *const u8, self.size) }
    }
}

struct SystemFontMap(Ashmem);

// SAFETY: System font map is only set once and is immutable after that.
unsafe impl Sync for SystemFontMap {}

// The mmap'ed regions of the system font map needs to be valid for the entire process lifetime as
// some data structures in `libminikin` directly point to it.
static SYSTEM_FONT_MAP: OnceLock<SystemFontMap> = OnceLock::new();

fn set_system_font_map(fd: OwnedFd) -> Result<&'static SystemFontMap> {
    let ashmem = Ashmem::try_from(fd)?;
    SYSTEM_FONT_MAP
        .set(SystemFontMap(ashmem))
        .map_err(|_| anyhow::anyhow!("Cannot update system font map once set"))?;
    Ok(SYSTEM_FONT_MAP.get().expect("system font map not set"))
}

struct SystemFontMapReader {
    data: &'static [u8],
    position: usize,
}

impl SystemFontMapReader {
    fn new(system_font_map: &'static SystemFontMap) -> Self {
        Self { data: system_font_map.0.as_ref(), position: 0 }
    }

    fn remaining_slice(&self) -> &[u8] {
        &self.data[self.position..]
    }

    fn consume(&mut self, size: usize) {
        self.position += size;
    }

    // Consumes minikin::FontStyle
    fn consume_font_style(&mut self) {
        // mWeight
        self.consume(std::mem::size_of::<u16>());
        // mSlant
        self.consume(std::mem::size_of::<u8>());
    }

    // Consumes bytes for android::Typeface
    fn consume_type_face(&mut self) {
        self.consume_font_style();
        // android::Typeface::Style
        self.consume(std::mem::size_of::<u8>());
        // Typeface base weight
        self.consume(std::mem::size_of::<i32>());
    }

    fn read_four_bytes(&mut self) -> [u8; 4] {
        let size = std::mem::size_of::<u32>();
        let bytes = self.remaining_slice()[..size].try_into().unwrap();
        self.consume(size);
        bytes
    }

    fn read_be_i32(&mut self) -> i32 {
        i32::from_be_bytes(self.read_four_bytes())
    }

    fn read_le_u32(&mut self) -> u32 {
        u32::from_le_bytes(self.read_four_bytes())
    }

    fn read_be_u32(&mut self) -> u32 {
        u32::from_be_bytes(self.read_four_bytes())
    }

    fn read_string(&mut self) -> Result<String> {
        let len = self.read_be_u32() as usize;
        let bytes = self.remaining_slice()[..len].to_vec();
        self.consume(len);
        Ok(String::from_utf8(bytes)?)
    }
}

pub fn load_system_font_map(fd: OwnedFd) -> Result<()> {
    atrace::trace_method!(AtraceTag::Graphics);

    let system_font_map = set_system_font_map(fd)?;
    let mut reader = SystemFontMapReader::new(system_font_map);
    let typeface_bytes_count = reader.read_be_i32();

    ffi::minikin_font_skia_factory_init();
    let mut bytes_read = 0usize;
    let collections =
        ffi::font_collection_read_vector_slice(reader.remaining_slice(), &mut bytes_read);
    reader.consume(bytes_read);

    let typeface_count = reader.read_le_u32();
    let mut font_collections = Vec::with_capacity(typeface_count as usize);
    for _ in 0..typeface_count {
        let idx = reader.read_le_u32();
        reader.consume_type_face();
        font_collections.push(ffi::vector_get(&collections, idx as usize));
    }

    if typeface_bytes_count as usize != reader.position {
        bail!(
            "Typeface bytes count does not match: expected {} but got {}",
            typeface_bytes_count,
            reader.position
        );
    }

    for font_collection in font_collections {
        let name = reader.read_string()?;
        if name == DEFAULT_FONT_FAMILY {
            ffi::system_fonts_register_default(font_collection.clone());
        }
        if matches!(
            name.as_str(),
            "serif" | "sans-serif" | "cursive" | "fantasy" | "monospace" | "system-ui"
        ) {
            cxx::let_cxx_string!(cname = name);
            ffi::system_fonts_register_fallback(&cname, font_collection.clone());
        }
        ffi::system_fonts_add_font_map(font_collection);
    }
    Ok(())
}

#[cxx::bridge]
pub mod ffi {
    #[namespace = "minikin"]
    unsafe extern "C++" {
        include!("SystemFontsBridge/SystemFontsBridge.h");

        type FontCollection;
        type FontCollectionPtrs;

        fn font_collection_read_vector_slice(
            data: &[u8],
            bytes_read: &mut usize,
        ) -> UniquePtr<FontCollectionPtrs>;
        fn vector_get(collections: &FontCollectionPtrs, index: usize) -> SharedPtr<FontCollection>;

        fn system_fonts_add_font_map(collection: SharedPtr<FontCollection>);
        fn system_fonts_register_default(collection: SharedPtr<FontCollection>);
        fn system_fonts_register_fallback(
            family_name: &CxxString,
            collection: SharedPtr<FontCollection>,
        );
    }

    #[namespace = "android"]
    unsafe extern "C++" {
        include!("SystemFontsBridge/SystemFontsBridge.h");

        fn minikin_font_skia_factory_init();
    }
}
