{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-25.11";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "aarch64-darwin" "x86_64-darwin" "x86_64-linux" "aarch64-linux" ];
      forEachSystem = nixpkgs.lib.genAttrs systems;

    in
    {
      devShells = forEachSystem (system:
        let
          pkgs = import nixpkgs { inherit system; };

        in
        {
          buildtime = pkgs.mkShell {
            packages = [
              pkgs.jdk25_headless
              (pkgs.gradle_9.override { java = pkgs.jdk25_headless; })
            ];

            JAVA_HOME = "${pkgs.jdk25_headless}";
            GRADLE_HOME = "${pkgs.gradle_9.override { java = pkgs.jdk25_headless; }}/lib/gradle";

            # Gradle uses an unsafe JDK operation, this flag removes the warning
            JDK_JAVA_OPTIONS = "--enable-native-access=ALL-UNNAMED";

            BUILD_COMMAND = "gradle build";
            UPDATE_IDIOMATIC_LOCK_COMMAND = "gradle dependencies --write-locks";

            IDE_PATH_VARS = "JAVA_HOME GRADLE_HOME";

            RELEASE_TYPE = "executable";
          };
        }
      );

      packages = forEachSystem (system:
        let
          pkgs = import nixpkgs { inherit system; };

        in
        {
          runtime = pkgs.writeShellApplication {
            name = "execute";
            runtimeInputs = [ pkgs.jdk25_headless pkgs.gh ];
            runtimeEnv.JAVA_HOME = "${pkgs.jdk25_headless}";
            text = ''
              idiomatic_artifacts_dir="$1"
              input_json="$2"
              # Guice uses an unsafe JDK operation, this flag removes the warning
              exec java -Dguice_custom_class_loading=CHILD -XX:MaxRAMPercentage=75.0 -cp "$idiomatic_artifacts_dir/*" io.materializer.Main "$input_json"
            '';
          };
        }
      );
    };
}
