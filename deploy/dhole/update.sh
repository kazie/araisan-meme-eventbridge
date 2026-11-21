#!/usr/bin/env bash
# meme-bridge auto-upgrade on dhole, run nightly by meme-bridge-update.timer.
#
# Fast-forwards the checkout (~/apps/meme-bridge/src -> ~/src/araisan-meme-eventbridge) to origin/master.
# When that commit isn't live and hasn't been tried, builds the release binary with the flake's toolchain
# (`nix run .#build`) and hands it to nanoda: `nanoda deploy meme-bridge --id <commit>` makes it a release,
# restarts the unit and goes back by itself if it doesn't stay up. Back by hand: `nanoda rollback meme-bridge`.
#
#   update.sh           build and deploy origin/master if it's new
#   update.sh --force   build and deploy HEAD even if it was tried before
#
# A failed build is tried again the next night; a commit that reached `nanoda deploy` is not (so a commit
# that nanoda rolled back doesn't restart the bridge every night). Log: journalctl --user -u meme-bridge-update
set -euo pipefail

main() {
  local app=meme-bridge
  local src=${MEME_BRIDGE_SRC:-$HOME/apps/$app/src}
  local state=${XDG_STATE_HOME:-$HOME/.local/state}/$app-update/last-deployed
  local force=${1-}

  cd "$src"
  git fetch --quiet origin master
  git merge --ff-only --quiet origin/master
  local rev
  rev=$(git rev-parse --short=12 HEAD)

  if [[ $force != --force ]]; then
    if [[ $(readlink "$HOME/apps/$app/araisan-meme-server.kexe") == "releases/$rev/"* ]]; then
      echo "$app: $rev is live, nothing to do"
      return 0
    fi
    if [[ -f $state && $(<"$state") == "$rev" ]]; then
      echo "$app: $rev was deployed before and isn't live (rolled back?); --force to try again"
      return 0
    fi
  fi

  echo "$app: building $rev: $(git log -1 --format=%s)"
  nix run .#build

  local staged
  staged=$(nanoda staging "$app")
  install -D -m 755 build/bin/host/releaseExecutable/araisan-meme-server.kexe "$staged"
  mkdir -p "$(dirname "$state")"
  echo "$rev" >"$state"
  nanoda deploy "$app" --id "$rev" --yes
}

# All in a function: the fast-forward above can rewrite this file while bash is still reading it.
main "$@"
exit
