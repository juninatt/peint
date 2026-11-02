#!/usr/bin/env bash
# Prints Markdown release notes for a version tag, built from the subjects
# of the commits since the previous version tag and grouped by commit type.
#
# Usage: release-notes.sh <tag> [<repository>]
#   e.g. release-notes.sh v1.1.0 juninatt/peint
set -euo pipefail

tag="$1"
repository="${2:-}"

previous=$(git describe --tags --abbrev=0 --match 'v*' "$tag^" 2>/dev/null || true)
range="${previous:+$previous..}$tag"

features=()
fixes=()
other=()

while IFS= read -r subject; do
    # "fix(canvas): fill pixels ..." -> type "fix", scope "canvas", "fill pixels ..."
    if [[ "$subject" =~ ^([a-z]+)(\(([^\)]+)\))?:\ (.*)$ ]]; then
        type="${BASH_REMATCH[1]}"
        scope="${BASH_REMATCH[3]}"
        description="${BASH_REMATCH[4]}"
    else
        type=""
        scope=""
        description="$subject"
    fi

    # The version bump itself isn't a change worth listing.
    if [[ "$type" == "build" && "$description" == "set version to "* ]]; then
        continue
    fi

    line="- ${description^}${scope:+ (\`$scope\`)}"
    case "$type" in
        feat) features+=("$line") ;;
        fix) fixes+=("$line") ;;
        *) other+=("$line") ;;
    esac
done < <(git log --reverse --format=%s "$range")

print_section() {
    local heading="$1"
    shift
    if (($# > 0)); then
        printf '## %s\n\n' "$heading"
        printf '%s\n' "$@"
        printf '\n'
    fi
}

print_section "Features" "${features[@]+"${features[@]}"}"
print_section "Fixes" "${fixes[@]+"${fixes[@]}"}"
print_section "Other changes" "${other[@]+"${other[@]}"}"

if [[ -n "$previous" && -n "$repository" ]]; then
    printf '**Full changelog**: https://github.com/%s/compare/%s...%s\n' "$repository" "$previous" "$tag"
fi
