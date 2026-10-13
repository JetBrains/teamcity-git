#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# ---------------------------------------------------------------------------
# Build a sample git repository: 50 commits, 5 branches, with merge commits
# and randomly generated content.
# ---------------------------------------------------------------------------

git init -q
git config user.name  "Sample Author"
git config user.email "sample@example.com"
git symbolic-ref HEAD refs/heads/main

WORDS=(alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu \
       xi omicron pi rho sigma tau upsilon phi chi psi omega quantum vector \
       matrix tensor scalar kernel buffer socket thread mutex cache token \
       cursor packet stream pixel shader vertex render parser lexer)
EXTS=(txt md py js go rs json yaml cfg log)

COMMIT_NUM=0

rand_word()  { echo "${WORDS[$((RANDOM % ${#WORDS[@]}))]}"; }
rand_ext()   { echo "${EXTS[$((RANDOM % ${#EXTS[@]}))]}"; }

# Write random content into a random file.
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

# Make a normal commit on the current branch.
commit() {
  random_change
  COMMIT_NUM=$((COMMIT_NUM + 1))
  git commit -q -m "$(rand_word): update $(rand_word) ($COMMIT_NUM)"
}

# Merge a branch into the current one (creates a merge commit).
merge() {
  local branch="$1"
  COMMIT_NUM=$((COMMIT_NUM + 1))
  git merge -q --no-ff -m "Merge branch '$branch' (#$COMMIT_NUM)" "$branch"
}

# --- main: initial history ---------------------------------------------------
echo "# Sample Repository" > README.md
git add README.md
COMMIT_NUM=$((COMMIT_NUM + 1))
git commit -q -m "Initial commit ($COMMIT_NUM)"
commit; commit; commit            # main now has some base history

# --- feature-a: branch, work, merge back ------------------------------------
git checkout -q -b feature-a
commit; commit; commit; commit; commit
git checkout -q main
commit                            # divergent work on main
merge feature-a

# --- feature-b: branch, work, merge back ------------------------------------
git checkout -q -b feature-b
commit; commit; commit; commit; commit; commit
git checkout -q main
commit; commit
merge feature-b

# --- feature-c: branch off, with a sub-branch feature-d ----------------------
git checkout -q -b feature-c
commit; commit; commit

git checkout -q -b feature-d      # feature-d branches off feature-c
commit; commit; commit; commit
git checkout -q feature-c
commit; commit
merge feature-d                   # merge feature-d into feature-c
commit

git checkout -q main
commit
merge feature-c                   # merge feature-c into main

# --- a few more commits spread around to top up history ----------------------
git checkout -q feature-a
commit; commit
git checkout -q feature-b
commit; commit
git checkout -q main
commit
merge feature-a
commit

# --- top up to exactly 50 commits, alternating across branches --------------
BRANCHES=(feature-a feature-b feature-c main)
idx=0
while [ "$COMMIT_NUM" -lt 49 ]; do
  git checkout -q "${BRANCHES[$((idx % ${#BRANCHES[@]}))]}"
  commit
  idx=$((idx + 1))
done

# final merge brings us to 50 and leaves a merge commit on the tip
git checkout -q main
if [ "$COMMIT_NUM" -lt 50 ]; then
  merge feature-c || commit
fi

git checkout -q main
echo "Built repo with $COMMIT_NUM commits."
