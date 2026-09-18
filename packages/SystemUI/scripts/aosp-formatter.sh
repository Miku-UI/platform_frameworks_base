#!/usr/bin/env bash

# --- Configuration ---
KTFMT_OVERRIDE_PATH=""
JAVA_FORMAT_OVERRIDE_PATH=""

# Default to AOSP style (4 spaces) for SysUI/Frameworks
GOOGLE_JAVA_FORMAT_FLAGS="--aosp"

# --- Color Definitions ---
if [ -t 1 ]; then
    RESET=$(tput sgr0 2>/dev/null || echo -e "\033[0m")
    BOLD=$(tput bold 2>/dev/null || echo -e "\033[1m")
    DIM=$(tput dim 2>/dev/null || echo -e "\033[2m")
    RED=$(tput setaf 1 2>/dev/null || echo -e "\033[31m")
    GREEN=$(tput setaf 2 2>/dev/null || echo -e "\033[32m")
    YELLOW=$(tput setaf 3 2>/dev/null || echo -e "\033[33m")
    BLUE=$(tput setaf 4 2>/dev/null || echo -e "\033[34m")
else
    RESET='' BOLD='' DIM='' RED='' GREEN='' YELLOW='' BLUE=''
fi

# --- Helper Functions ---

log_info() { printf "${CYAN}%s${RESET}\n" "$1"; }
log_dim() { printf "${DIM}%s${RESET}\n" "$1"; }
log_warn() { printf "${YELLOW}%s${RESET}\n" "$1"; }
log_error() { printf "${RED}%s${RESET}\n" "$1" >&2; }
die() { log_error "$1"; exit "${2:-1}"; }

abspath() {
    local p="$1"
    if [[ "$p" != /* ]]; then p="$PWD/$p"; fi
    echo "$p" | awk 'BEGIN{OFS="/"} {
        n=split($0, t, "/");
        for(i=1; i<=n; i++) {
            if(t[i]=="" || t[i]==".") continue;
            if(t[i]=="..") { if(x>0) delete a[x--]; }
            else a[++x]=t[i];
        }
        path=""; for(i=1; i<=x; i++) path=path "/" a[i];
        print (path=="" ? "/" : path)
    }'
}

resolve_ktfmt_path() {
    if [[ -n "$KTFMT_OVERRIDE_PATH" && -x "$KTFMT_OVERRIDE_PATH" ]]; then abspath "$KTFMT_OVERRIDE_PATH"; return 0; fi
    if [[ -x "./ktfmt.sh" ]]; then abspath "./ktfmt.sh"; return 0; fi
    if [[ -n "$ANDROID_BUILD_TOP" && -x "$ANDROID_BUILD_TOP/external/ktfmt/ktfmt.sh" ]]; then abspath "${ANDROID_BUILD_TOP}/external/ktfmt/ktfmt.sh"; return 0; fi
    return 1
}

resolve_java_format_path() {
    if [[ -n "$JAVA_FORMAT_OVERRIDE_PATH" && -x "$JAVA_FORMAT_OVERRIDE_PATH" ]]; then abspath "$JAVA_FORMAT_OVERRIDE_PATH"; return 0; fi
    if [[ -x "./google-java-format" ]]; then abspath "./google-java-format"; return 0; fi
    if [[ -n "$ANDROID_BUILD_TOP" && -x "$ANDROID_BUILD_TOP/prebuilts/tools/common/google-java-format/google-java-format" ]]; then abspath "${ANDROID_BUILD_TOP}/prebuilts/tools/common/google-java-format/google-java-format"; return 0; fi
    return 1
}

check_inside_git_repo() {
    if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then die "Error: Not a Git repository."; fi
    git rev-parse --show-toplevel
}

detect_ide_config_file() {
    local git_root="$1"
    if [[ -f "$git_root/.idea/workspace.xml" ]]; then echo "$git_root/.idea/workspace.xml"; return 0; fi
    if [[ -f "$git_root/.idea/changelists.xml" ]]; then echo "$git_root/.idea/changelists.xml"; return 0; fi
    return 1
}

is_in_rebase() {
    local git_dir; git_dir=$(git rev-parse --git-dir)
    if [[ -d "$git_dir/rebase-merge" || -d "$git_dir/rebase-apply" ]]; then return 0; fi
    return 1
}

get_default_base_branch() {
    local candidates=("m/main" "goog/main" "master" "develop" "main")
    local upstream; upstream=$(git rev-parse --abbrev-ref HEAD@{upstream} 2>/dev/null)
    if [[ -n "$upstream" && "$upstream" != "HEAD@{upstream}" ]]; then echo "$upstream"; return; fi
    for branch in "${candidates[@]}"; do
        if git rev-parse --verify "$branch" >/dev/null 2>&1; then echo "$branch"; return; fi
        if git rev-parse --verify "origin/$branch" >/dev/null 2>&1; then echo "origin/$branch"; return; fi
    done
}

get_changed_line_flags() {
    local file="$1"
    local base="${2:-HEAD}"
    local diff_output; diff_output=$(git diff -U0 "$base" -- "$file")
    echo "$diff_output" | awk '
    /^@@/ {
        gsub(/@@/, "", $0)
        split($2, parts, ",")
        start = substr(parts[1], 2)
        count = (length(parts) > 1) ? parts[2] : 1
        if (count > 0) {
            end = start + count - 1
            printf "--lines %d:%d ", start, end
        }
    }'
}

parse_git_status() {
    echo "$1" | while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        local path="${line:3}"
        if [[ "$path" == *" -> "* ]]; then path="${path##* -> }"; fi
        path="${path%\"}"; path="${path#\"}"
        if [[ "$path" == *.kt || "$path" == *.kts || "$path" == *.java ]]; then echo "$path"; fi
    done
}

configure_java_env() {
    local git_root="$1"
    local top="${ANDROID_BUILD_TOP:-$git_root}"
    local os_name; os_name=$(uname -s | tr '[:upper:]' '[:lower:]')
    local arch_name; arch_name=$(uname -m)
    local jdk_path=""

    if [[ "$os_name" == "linux" ]]; then
        if [[ -d "$top/prebuilts/jdk/jdk21/linux-x86" ]]; then jdk_path="$top/prebuilts/jdk/jdk21/linux-x86"; fi
    elif [[ "$os_name" == "darwin" ]]; then
        if [[ "$arch_name" == "arm64" && -d "$top/prebuilts/jdk/jdk21/darwin-arm64" ]]; then
             jdk_path="$top/prebuilts/jdk/jdk21/darwin-arm64"
        elif [[ -d "$top/prebuilts/jdk/jdk21/darwin-x86" ]]; then
             jdk_path="$top/prebuilts/jdk/jdk21/darwin-x86"
        fi
    fi

    if [[ -n "$jdk_path" && -x "$jdk_path/bin/java" ]]; then
        export JAVA_HOME="$jdk_path"
        export PATH="$jdk_path/bin:$PATH"
    fi
}

run_ktfmt() {
    local script_path="$1"; local mode="$2"; shift 2; local files=("$@")
    if [[ ${#files[@]} -eq 0 ]]; then return 0; fi
    local args=("--kotlinlang-style")
    [[ "$mode" == "check" ]] && args+=("--dry-run" "--set-exit-if-changed")

    local out
    out=$("$script_path" "${args[@]}" "${files[@]}" 2>&1)
    local ret=$?

    if [[ $ret -ne 0 ]]; then
        if [[ "$mode" == "check" ]]; then
            echo "$out"
        else
            printf "${RED}ktfmt error output:${RESET}\n" >&2
            echo "$out" | sed 's/^/  /' >&2
        fi
        return $ret
    fi
    return 0
}

run_java_format() {
    local script_path="$1"; local action="$2"; local partial="$3"; local diff_base="$4"; shift 4; local files=("$@")
    if [[ ${#files[@]} -eq 0 ]]; then return 0; fi

    local args=()
    [[ -n "$GOOGLE_JAVA_FORMAT_FLAGS" ]] && args+=($GOOGLE_JAVA_FORMAT_FLAGS)

    if [[ "$partial" == "true" ]]; then
        local any_fail=false
        for f in "${files[@]}"; do
            local line_flags; line_flags=$(get_changed_line_flags "$f" "$diff_base")

            if [[ -n "$line_flags" ]]; then
                read -ra FLAG_ARRAY <<< "$line_flags"

                if [[ ${#FLAG_ARRAY[@]} -eq 0 ]]; then
                    log_dim "Skipping $f (No changed lines detected)"
                    continue
                fi

                if [[ "$action" == "check" ]]; then
                    # Check partial
                    if ! "$script_path" "${args[@]}" --dry-run --set-exit-if-changed "${FLAG_ARRAY[@]}" "$f" >/dev/null 2>&1; then
                        any_fail=true
                        printf "   ${RED}✘ %s${RESET}\n" "$f"
                        "$script_path" "${args[@]}" "${FLAG_ARRAY[@]}" "$f" | diff -u --color=always --label "Original" --label "Formatted" "$f" - | tail -n +3 | head -n 10 | sed 's/^/     /'
                    fi
                else
                    # Format partial
                    if ! "$script_path" "${args[@]}" "--replace" "${FLAG_ARRAY[@]}" "$f"; then any_fail=true; fi
                fi
            else
                log_dim "Skipping $f (No changed lines)"
            fi
        done
        [[ "$any_fail" == "true" ]] && return 1 || return 0
    else
        if [[ "$action" == "check" ]]; then
            local any_fail=false
            args+=("--dry-run" "--set-exit-if-changed")
            for f in "${files[@]}"; do
                if ! "$script_path" "${args[@]}" "$f" >/dev/null 2>&1; then
                   any_fail=true
                   printf "   ${RED}✘ %s${RESET}\n" "$f"
                   "$script_path" "${args[@]}" "$f" | diff -u --color=always --label "Original" --label "Formatted" "$f" - | tail -n +3 | head -n 10 | sed 's/^/     /'
                fi
            done
            if [[ "$any_fail" == "true" ]]; then return 1; else return 0; fi
        else
            args+=("--replace")
            local out; out=$("$script_path" "${args[@]}" "${files[@]}" 2>&1)
            local ret=$?;
            if [[ $ret -ne 0 ]]; then
                printf "${RED}google-java-format error output:${RESET}\n" >&2
                echo "$out" | sed 's/^/  /' >&2
                return $ret
            fi
            return 0
        fi
    fi
}

handle_rebase_step() {
    local git_root="$1"; local auto_yes="$2"; local ktfmt_path="$3"; local java_path="$4"; local partial="$5"
    local commit_hash; commit_hash=$(git log -1 --pretty=%h HEAD)
    local commit_msg; commit_msg=$(git log -1 --pretty=%s HEAD | cut -c 1-60)

    echo; printf "${BLUE}${BOLD} COMMIT %s ${RESET}| ${BOLD}%-60s ${RESET}\n" "$commit_hash" "$commit_msg"
    local files_str; files_str=$(git show --pretty=format: --name-only --diff-filter=ACM HEAD -- "*.kt" "*.kts" "*.java")
    if [[ -z "$files_str" ]]; then printf " ➜ Status: %s✔ Clean%s\n" "$GREEN" "$RESET"; exit 0; fi

    local -a kt_files; local -a java_files
    while IFS= read -r f; do
        [[ -z "$f" ]] && continue
        if [[ "$f" == *.java ]]; then java_files+=("$f"); else kt_files+=("$f"); fi
    done <<< "$files_str"

    if [[ ${#kt_files[@]} -gt 0 ]]; then printf " ${BOLD}Kotlin:${RESET}\n"; for f in "${kt_files[@]}"; do printf "  • %s\n" "$f"; done; fi
    if [[ ${#java_files[@]} -gt 0 ]]; then printf " ${BOLD}Java:${RESET}\n"; for f in "${java_files[@]}"; do printf "  • %s\n" "$f"; done; fi

    local fail=false
    if [[ ${#kt_files[@]} -gt 0 ]]; then run_ktfmt "$ktfmt_path" "format" "${kt_files[@]}" || fail=true; fi
    if [[ ${#java_files[@]} -gt 0 ]]; then run_java_format "$java_path" "format" "$partial" "HEAD^" "${java_files[@]}" || fail=true; fi

    if [[ "$fail" == "true" ]]; then printf " ➜ Status: %s✘ Error.%s\n" "$RED" "$RESET"; exit 1; fi

    if git diff --quiet "${kt_files[@]}" "${java_files[@]}"; then printf " ➜ Status: %s✔ Verified%s\n" "$GREEN" "$RESET"; exit 0; fi

    printf " ➜ Status: %s✎ Changes applied.%s\n" "$YELLOW" "$RESET"
    if [[ "$auto_yes" != "true" ]]; then
        while true; do
            printf "${GREEN}   Amend? (Y/n/abort): ${RESET}"
            read -r c; c=$(echo "$c" | tr '[:upper:]' '[:lower:]')
            case "$c" in y|"" ) break ;; n ) exit 0 ;; a ) exit 1 ;; * ) ;; esac
        done
    fi
    git add "${kt_files[@]}" "${java_files[@]}"
    local out
    if out=$(git commit --amend --no-edit --no-verify 2>&1); then
        printf "   ${GREEN}Amended.${RESET}\n"
        exit 0
    else
        printf "   ${RED}Amend failed:${RESET}\n"
        echo "$out" | sed 's/^/     /'
        exit 1
    fi
}

show_help() {
    printf "Format Kotlin/Java files (AOSP)\n\n"
    printf "${BOLD}MODES:${RESET}\n"
    printf "  -w, --work      Format working directory.\n"
    printf "  -r, --rebase    Rebase and format entire branch.\n"
    printf "  --dry-run       Check only.\n"
    printf "\n"
    printf "${BOLD}OPTIONS:${RESET}\n"
    printf "  -a, --all-lines Format ENTIRE file (Java default is changed lines only; Kotlin is all lines always - this is the current default of our preupload hooks).\n"
    printf "  --base <br>     Defaults to m/main. Set this if you want to rebase against a different base branch.\n"
    printf "  -y, --yes       Auto-confirm.\n"
}

main() {
    local mode=""
    local base_branch=""; local auto_yes=false; local internal_rebase_flag=false; local quiet_start=false
    local partial_format=true
    local -a files_args
    local dry_run=false

    local ktfmt_path; ktfmt_path=$(resolve_ktfmt_path)
    local java_path; java_path=$(resolve_java_format_path)

    if [[ $# -eq 0 ]]; then show_help; exit 1; fi
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --base) base_branch="$2"; shift 2 ;;
            --check|--dry-run) dry_run=true; shift ;;
            -w|--work) mode="work"; shift ;;
            -r|--rebase) mode="rebase"; shift ;;
            -c|--changed) partial_format=true; shift ;;
            -a|--all-lines) partial_format=false; shift ;;
            -y|--yes) auto_yes=true; shift ;;
            -q|--quiet) quiet_start=true; shift ;;
            --_format_commit_for_rebase) internal_rebase_flag=true; shift ;;
            -h|--help) show_help; exit 0 ;;
            *)
                if [[ -f "$1" ]]; then files_args+=("$1"); shift
                else die "Unknown argument: $1"; fi
                ;;
        esac
    done

    [[ -z "$ktfmt_path" ]] && die "ktfmt not found."
    [[ -z "$java_path" ]] && die "google-java-format not found."

    local git_root; git_root=$(check_inside_git_repo)
    cd "$git_root" || die "Root error."

    # Force JDK 21 (AOSP Hermetic)
    configure_java_env "$git_root"

    if [[ "$internal_rebase_flag" == "true" ]]; then
        handle_rebase_step "$(pwd)" "$auto_yes" "$ktfmt_path" "$java_path" "$partial_format"
        exit 0
    fi

    # --- Mode: REBASE ---
    if [[ "$mode" == "rebase" ]]; then
        if is_in_rebase; then die "Rebase in progress."; fi
        [[ -z "$base_branch" ]] && base_branch=$(get_default_base_branch)
        printf "Base: ${GREEN}%s${RESET}\n" "$base_branch"

        local stash_name="fmt_rebase_stash_$(date +%s)"; local stashed=false
        local ide_config_file=""; local ide_config_backup=""
        local possible_ide_file; possible_ide_file=$(detect_ide_config_file "$git_root")
        if [[ -n "$possible_ide_file" ]]; then ide_config_file="$possible_ide_file"; fi

        if [[ -n $(git status --porcelain) ]]; then
            printf " ➜ Stashing... \n"
            if [[ -n "$ide_config_file" ]]; then
                ide_config_backup=$(mktemp 2>/dev/null || mktemp -t 'ide_backup')
                cp "$ide_config_file" "$ide_config_backup"
            fi
            if ! git stash push -q -u -m "$stash_name"; then
                die "Stash failed. Aborting rebase to avoid data loss."
            fi
            stashed=true
        fi

        local script_abs_path; script_abs_path="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"
        local exec_cmd="$script_abs_path --_format_commit_for_rebase"
        [[ "$auto_yes" == "true" ]] && exec_cmd="$exec_cmd --yes"
        [[ "$partial_format" == "true" ]] && exec_cmd="$exec_cmd --changed"

        log_info "Starting rebase..."
        if git rebase --quiet --exec "$exec_cmd" "$base_branch"; then
            printf "\n${BLUE}${BOLD} REBASE COMPLETE ${RESET}\n"
        else
            log_error "Rebase failed."; [[ "$stashed" == "true" ]] && log_warn "Stash: '$stash_name'."
            [[ -f "$ide_config_backup" ]] && rm "$ide_config_backup"; exit 1
        fi

        if [[ "$stashed" == "true" ]]; then
            printf " ➜ Restoring stash...\n"
            if git stash pop --index -q; then
                if [[ -n "$ide_config_backup" && -f "$ide_config_backup" ]]; then
                    cp "$ide_config_backup" "$ide_config_file"; touch "$ide_config_file"
                    local i=0; while [ $i -lt 4 ]; do sleep 0.5; cp "$ide_config_backup" "$ide_config_file"; ((i++)); done
                    rm "$ide_config_backup"
                fi
                "$script_abs_path" -w -q $([[ "$auto_yes" == "true" ]] && echo "-y") $([[ "$partial_format" == "true" ]] && echo "--changed")
            else
                log_warn "Stash pop failed. You may have conflicts."
                git status --short
            fi
        fi
        exit 0
    fi

    # --- Mode: WORK / DRY-RUN ---
    if [[ "$mode" == "work" || "$dry_run" == "true" || ${#files_args[@]} -gt 0 ]]; then
        local -a kt_files; local -a java_files
        local diff_base="HEAD"

        # 1. Gather Files
        if [[ ${#files_args[@]} -gt 0 ]]; then
             for f in "${files_args[@]}"; do
                if [[ "$f" == *.java ]]; then java_files+=("$f"); elif [[ "$f" == *.kt || "$f" == *.kts ]]; then kt_files+=("$f"); fi
             done
        elif [[ -n "$PREUPLOAD_FILES" ]]; then
             for f in $PREUPLOAD_FILES; do
                if [[ "$f" == *.java ]]; then java_files+=("$f"); elif [[ "$f" == *.kt || "$f" == *.kts ]]; then kt_files+=("$f"); fi
             done
        else
            [[ "$quiet_start" == "false" ]] && log_info "Gathering files..."
            local temp_list; temp_list=$(git status --porcelain -uall)
            while IFS= read -r f; do
                 if [[ "$f" == *.java ]]; then java_files+=("$f"); elif [[ "$f" == *.kt || "$f" == *.kts ]]; then kt_files+=("$f"); fi
            done < <(parse_git_status "$temp_list")
        fi

        if [[ ${#kt_files[@]} -eq 0 && ${#java_files[@]} -eq 0 ]]; then [[ "$quiet_start" == "false" ]] && echo "No files."; exit 0; fi

        # 2. Set Action Mode
        local action="format"
        [[ "$dry_run" == "true" ]] && action="check"

        # 3. Print Summary
        if [[ ${#java_files[@]} -gt 0 ]]; then
            printf "${BOLD}Changed Java files (format withgoogle-java-format):${RESET}\n";
            for f in "${java_files[@]}"; do printf " • %s\n" "$f"; done
            if [[ "$partial_format" == "true" ]]; then printf "${DIM}(Changed lines only)${RESET}\n"; fi
        fi
        if [[ ${#kt_files[@]} -gt 0 ]]; then
            printf "${BOLD}Changed Kotlin files (format with ktfmt):${RESET}\n";
            for f in "${kt_files[@]}"; do printf " • %s\n" "$f"; done
        fi

        # 4. Confirmation (Only for Format mode)
        if [[ "$action" == "format" && "$auto_yes" != "true" ]]; then
            printf "\n${GREEN}Proceed? (y/N): ${RESET}"
            read -r c; c=$(echo "$c" | tr '[:upper:]' '[:lower:]')
            if [[ "$c" != "y" ]]; then log_warn "Cancelled."; exit 0; fi
        fi

        # 5. Execute
        local fail=false

        if [[ ${#kt_files[@]} -gt 0 ]]; then
            run_ktfmt "$ktfmt_path" "$action" "${kt_files[@]}" || fail=true
        fi

        if [[ ${#java_files[@]} -gt 0 ]]; then
            run_java_format "$java_path" "$action" "$partial_format" "$diff_base" "${java_files[@]}" || fail=true
        fi

        if [[ "$fail" == "true" ]]; then
            if [[ "$action" == "check" ]]; then
                printf "${RED}Formatting verification failed.${RESET}\n"
                local fix_script_path="./$(basename "$0")"
                if [[ -n "$ANDROID_BUILD_TOP" ]]; then
                     fix_script_path="${ANDROID_BUILD_TOP}/development/scripts/aosp-formatter.sh"
                fi
                printf "${YELLOW}Run %s --work (or --rebase) to fix.${RESET}\n" "$fix_script_path"
                exit 1
            else
                die "Failed."
            fi
        else
            printf " ➜ Status: %s✔ Done%s\n" "$GREEN" "$RESET"
        fi
        exit 0
    fi

    show_help
}
trap 'printf "\n${YELLOW}Interrupted.${RESET}\n"; exit 1' SIGINT
main "$@"