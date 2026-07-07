#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# ---------------------------------------------------------------------------
# Add 20 more commits across 3 new branches (feature-e/f/g), with merges,
# continuing the existing repository. Random content as before.
# ---------------------------------------------------------------------------

git config user.name  "Sample Author"
git config user.email "sample@example.com"

WORDS=(alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu \
       xi omicron pi rho sigma tau upsilon phi chi psi omega quantum vector \
       matrix tensor scalar kernel buffer socket thread mutex cache token \
       cursor packet stream pixel shader vertex render parser lexer)
EXTS=(txt md py js go rs json yaml cfg log)

# continue numbering from existing history
COMMIT_NUM=$(git rev-list --all --count)

rand_word()  { echo "${WORDS[$((RANDOM % ${#WORDS[@]}))]}"; }
rand_ext()   { echo "${EXTS[$((RANDOM % ${#EXTS[@]}))]}"; }

random_change() {
  local fname="$(rand_word)_$(rand_word).$(rand_ext)"
  local lines=$((RANDOM % 8 + 2))
  {
    echo "# $(rand_word) module — generated content"
    for ((i = 0; i < lines; i++)); do
      echo "$(rand_word) $(rand_word) $((RANDOM)) $(rand_word) $((RANDOM % 1000))"
    done
  } >> "$fname"
  git add "$fname"
}

commit() {
  random_change
  COMMIT_NUM=$((COMMIT_NUM + 1))
  git commit -q -m "$(rand_word): update $(rand_word) ($COMMIT_NUM)"
}

merge() {
  local branch="$1"
  COMMIT_NUM=$((COMMIT_NUM + 1))
  git merge -q --no-ff -m "Merge branch '$branch' (#$COMMIT_NUM)" "$branch"
}

git checkout -q main

# --- feature-e ---------------------------------------------------------------
git checkout -q -b feature-e
commit; commit; commit; commit          # 51-54

# --- feature-f ---------------------------------------------------------------
git checkout -q main
git checkout -q -b feature-f
commit; commit; commit; commit          # 55-58

# --- feature-g (branches off feature-e) -------------------------------------
git checkout -q feature-e
git checkout -q -b feature-g
commit; commit; commit                  # 59-61

git checkout -q feature-e
commit                                  # 62
merge feature-g                         # 63 (merge)

git checkout -q main
commit                                  # 64
merge feature-e                         # 65 (merge)

commit                                  # 66
merge feature-f                         # 67 (merge)

git checkout -q feature-f
commit                                  # 68
git checkout -q main
commit                                  # 69
merge feature-f                         # 70 (merge)

git checkout -q main
echo "Repo now has $COMMIT_NUM commits."
