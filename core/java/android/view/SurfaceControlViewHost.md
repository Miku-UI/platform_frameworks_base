## SurfaceControlViewHost

**SurfaceControlViewHost** takes a standard Android View hierarchy and renders it onto a dedicated SurfaceControl. This SurfaceControl can then be embedded into another application's window in a different process. The primary use case is to display UI from one application process within another. Callers like SystemUI also use this to attach a View hierarchy to an arbitrary SurfaceControl. This can be useful to create and show windows not managed by WindowManagerService.

**SurfacePackage** encapsulates the SurfaceControl and necessary metadata from SurfaceControlViewHost. After a SurfaceControlViewHost is set up, `SurfaceControlViewHost#getSurfacePackage()` returns this object. The SurfacePackage is designed to be sent across process boundaries via Binder to a Host Process.

**Remote process** is an Android application process that provides a View hierarchy to be displayed elsewhere. It uses SurfaceControlViewHost to render its UI. The rendered output is then packaged into a SurfacePackage and sent to the Host Process. Examples include a sandboxed process for ads or a system component providing a secure UI.

**Host process** is an Android application process that receives and displays the UI content from a Remote Process. The Host Process integrates a SurfaceView into its own View hierarchy. It receives a SurfacePackage from the Remote Process and uses `SurfaceView.setChildSurfacePackage()` to make the content from the Remote Process visible within its SurfaceView.

**Remote View** refers to the `android.view.View` or View hierarchy that is created, managed, and rendered inside the Remote Process. This is the content that is passed to `SurfaceControlViewHost.setView()`. This View hierarchy operates entirely within the memory space of the Remote Process, isolated from the Host Process's View system.

**Host View** refers to the `android.view.View` hierarchy belonging to the application running in the Host Process. This hierarchy contains a SurfaceView instance, which acts as a window or placeholder where the content from the Remote View will be displayed once the SurfacePackage is attached.

**Remote Window** while not a `android.view.Window` in the traditional sense, the rendering surface managed by SurfaceControlViewHost for the Remote View acts as an isolated rendering context. The Remote Process renders its UI "into" this surface, which is separate from any standard application window. This surface is what gets embedded.

**Host Window** is the standard `android.view.Window` associated with the Activity or other windowing component within the Host Process. The Host View hierarchy, including the SurfaceView, is drawn within this Host Window. The content rendered by the Remote Process is composited by the system onto the Host Window at the location of the SurfaceView.


### Use Cases
#### Remote Rendering - Embedding a View hierarchy from one process to another
1. Host process binds to a service in the remote process.
2. Host process establishes a side channel (maybe Binder), passing its InputTransferToken, `Display ID`, initial dimensions of the View to be rendered.
3. Remote process creates SurfaceControlViewHost using the received information.
4. Remote process creates its View hierarchy and attaches it via `SurfaceControlViewHost#setView()`.
5. Remote process creates the SurfacePackage from `SurfaceControlViewHost#getSurfacePackage()`.
6. Remote process sends the SurfacePackage back to the host process, via the side channel.
7. Host process receives the SurfacePackage and sets it on a SurfaceView using `SurfaceView.setChildSurfacePackage()`.
8. Host process takes ownership of the SurfacePackage, remote process releases its instance of the object.
9. Ongoing communication happens through a side channel created by the system. Host process communicates configuration changes, and lifecycle changes to the Remote process via the `ISurfaceControlViewHost` interface. Remote process communicates layout param updates and back key propagation via the `ISurfaceControlViewHostParent` interface.


#### Task Overlay Windows
1. System service creates SurfaceControlViewHost using a DisplayID and a `null` host InputTransferToken.
2. System service creates its View hierarchy and attaches it via `SurfaceControlViewHost#setView()`.
3. System service calls `WindowManagerInternal#addTrustedTaskOverlay` with the task Id and the SurfacePackage from `SurfaceControlViewHost#getSurfacePackage()`.


### Composition Order with Remote Rendering
SurfacePackage when attached to a SurfaceView will be layered on top of the SurfaceView surface. This will be below any additional windows created by the activity.

#### Example shown in top to bottom composition order
1. Child Window (Popup Window)
2. SurfacePackage set to SurfaceView A
3. SurfaceView A with composition order >= 0
4. Main Window
5. SurfaceView B with composition order < 0

### Focus Handling and IME Support

The View hierarchy within the SurfaceControlViewHost receives its own InputChannel. InputDispatcher routes key events directly to the process owning the SurfaceControlViewHost based on focus state. In remote rendering use cases, the remote View does not automatically gain focus since the remote Window is not managed by WindowManager. Instead it gains focus when requested by the host process or by the system when the user taps on it. IME is supported on remote Views. If the remote Window is focused, it is able to request showing the IME.

### Touch Handling

InputDisptcher sends touch events to the remote Window via hit testing. If the remote Window is attached to a SurfaceView that is z-ordered above, all touches to the remote Window will be received by the remote process.
If the SurfaceView is z-ordered below, then the remote Window will be occluded by the host window and touches will not reach the remote Window. If the host Window has a cutout for its touchable region to let touches go through, the touches will still be dropped because the remote Window will be considered occluded by InputDispatcher.

### OnBackInvokedCallback Handling

The OnBackInvokedCallback registered from the View hierarchy within the SurfaceControlViewHost can be triggered when a back gesture occurs if the SurfaceControlViewHost has been granted input focus.

### Insets

Remote Windows attached to a host Window will not get inset callbacks. The host view hierarchy is expected to handle insets since the remote view is apart of its view hierarchy. In some cases where the remote Window is not attached to a host window but directly into the WindowManager hierarchy via `WindowManagerInternal#addTrustedTaskOverlay` the Remote window will be receive of inset callbacks.

### Accessibility

When a `SurfacePackage` is attached to a `SurfaceView`, the accessibility hierarchy from the remote view is automatically embedded into the host application's view hierarchy. The `SurfaceView` acts as the bridge, making the remote view's accessibility tree a child of the `SurfaceView`'s accessibility node. This integration allows accessibility services, such as screen readers, to navigate and interact with the remote content as if it were a native part of the host application.

By default, accessibility embedding is enabled. However, it can be disabled if the embedded content is purely decorative, or if its accessibility information is represented elsewhere in the host's UI. The `SurfaceView` provides the `@hide` `setAccessibilityHierarchyEmbeddingEnabled(boolean)` method to disable this feature. If the `SurfaceView` itself is decorative and should be excluded from the accessibility hierarchy, `View#setImportantForAccessibility(int)` should be considered.

### Security Considerations
SurfaceControlViewHost can be used to provide a sandboxed environment.

* Process Isolation: The core security benefit comes from the fact that the remote View hierarchy runs in a separate process from the host application. Each process has its own memory space preventing direct access to other processes' data and resources. The process hosting the remote View can be run with a restricted set of permissions.

* Rendering Isolation: The host process doesn't have direct access to the drawing commands, assets, or internal state of the remote Window. The rendering output is shared via a SurfaceControl. The host only controls the position and transformation of the SurfaceControl, not its content. The host process can't read the buffers from the remote process without taking a screenshot or using MediaProjection, both of which require elevated permissions. Without elevated privileges, when a process tries to take a screenshot of a SurfaceControl owned by a different UID, its content will be blackout. Similarly, when screen recording, SurfaceControls owned by a different UID will be omitted from the screen recording. To verify if the remote Window is actually fully visible, the embedding process can use `WindowManager#registerTrustedPresentationListener` API to determine whether the content is being partially occluded by the host. The final on-screen image is composited by SurfaceFlinger, which takes the buffers from the host process and the remote process (via their respective SurfaceControls) and composites them securely. If the remote Window is below the host window but shown via a transparent region in the host, this cannot be verified by the system and the TrustedPresentationListener will report the remote window as occluded.

* Input Isolation: The remote Window has its own InputChannel. This allows input events within the bounds of the remote Window to be dispatched directly to the process owning the window, without the host process being able to inspect or modify the input stream. For touch events  to reach the remote Window, the remote Window must be above the host, otherwise the remote Window will be considered occluded and touch events will be dropped.

### Alternatives
While SurfaceControlViewHost is a great choice for cross-process UI, or for creating windows not managed by WindowManager, depending on the use cases, other solutions may be more appropriate:

*   **Embedding Static Content or non-View Hierarchy**: For scenarios where you only need to display static content or content from a custom graphics producer (not a standard Android View hierarchy), the client process can render directly into a SurfaceControl using methods like `SurfaceControl.Transaction#setBuffer`. A parent to this SurfaceControl can then be passed to the host process. The host can parent this SurfaceControl to its own by using `AttachedSurfaceControl#buildReparentTransaction`.
*   **Embedding Tasks**: To embed an entire task from another application, TaskView is the recommended component.
*   **Embedding Activities**: To embed an entire activity from another application, investigate ActivityEmbedding.







