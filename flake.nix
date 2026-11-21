{
  description = "araisan-meme-eventbridge - Kotlin/Native WebSocket pub/sub bridge";

  # Nix pins the toolchain (the JDK that runs Gradle); Gradle still resolves the dependencies from
  # gradle/libs.versions.toml and the wrapper picks the Gradle version. That keeps Dependabot's Gradle
  # PRs mergeable as they are: a sandboxed `nix build` would need a lock of every Maven artifact and
  # Kotlin/Native download, which Dependabot can't update. flake.lock (nixpkgs, so the JDK) is bumped
  # by Dependabot's "nix" ecosystem.
  #
  #   nix run .#build                    the release binary: build/bin/host/releaseExecutable/araisan-meme-server.kexe
  #   nix run .#build -- hostTest ...    any other Gradle tasks
  #   nix develop                        a shell with the same JDK, for ./gradlew
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
      jdk = pkgs: pkgs.jdk25_headless;
    in
    {
      packages = forAllSystems (pkgs: {
        # Runs Gradle in the current directory (the checkout), so it isn't a sandboxed build: Gradle
        # downloads the dependencies and Kotlin/Native's toolchain (~/.konan) itself.
        build = pkgs.writeShellApplication {
          name = "meme-bridge-build";
          runtimeInputs = [ (jdk pkgs) ];
          text = ''
            export JAVA_HOME=${(jdk pkgs).home}
            if [ ! -x ./gradlew ]; then
              echo "meme-bridge-build: run it in the araisan-meme-eventbridge checkout" >&2
              exit 1
            fi
            if [ "$#" -eq 0 ]; then
              set -- linkReleaseExecutableHost
            fi
            exec ./gradlew --no-daemon "$@"
          '';
        };
        default = self.packages.${pkgs.stdenv.hostPlatform.system}.build;
      });

      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          packages = [ (jdk pkgs) ];
          JAVA_HOME = (jdk pkgs).home;
        };
      });

      formatter = forAllSystems (pkgs: pkgs.nixfmt);
    };
}
