# Releasing

The OCL validation companion has its own feature and p2 repository. Never publish its artifacts into
the core EMF Model Generator update site.

## Versioning

Use semantic versions for the public API and matching OSGi versions. Development metadata uses a
Maven `-SNAPSHOT` and an OSGi `.qualifier`; Tycho replaces the qualifier reproducibly during the
build. Update a reactor version with the pinned Tycho versions plug-in:

```sh
./mvnw org.eclipse.tycho:tycho-versions-plugin:5.0.4:set-version \
  -DnewVersion=0.1.0-SNAPSHOT
```

Review changes to every POM, `Bundle-Version`, `feature.xml`, dependency range, and `category.xml`.
Public API compatibility must be assessed before changing the major or minor version. Dependency
baseline or major-version changes require updating the target, manifests, installation test,
README, and `DEPENDENCIES.md` together.

## Release checklist

1. Start from a clean worktree on the intended release commit and use Java 21.
2. Confirm the target uses the named Eclipse/OCL release repositories and the intended explicit
   EMF Model Generator Maven artifact version. Verify that resolved bundles remain within the
   manifest's semantic-version compatibility ranges and that a release does not accidentally rely
   only on an unpublished local artifact.
3. Run `./mvnw clean verify -Pjacoco,repository-verification`. The `repository-verification`
   profile is a release-only/local gate and is deliberately not enabled in GitHub Actions. The
   command must pass without a sibling core checkout. CI runs `./mvnw clean verify -Pjacoco` on
   Linux and plain `./mvnw clean verify` on Windows and macOS.
   During development, the test bundle alone can be run with:

   ```sh
   ./mvnw verify -am -pl :io.github.lorenzobettini.emfmodelgenerator.ocl.tests \
     -Dtycho.localArtifacts=ignore
   ```

   It is not a release verification substitute because it does not assemble or install the p2
   repository.
4. Inspect the JaCoCo report and test reports. Do not lower the 100% line and branch gates for
   hand-written non-internal production code to make a release pass.
5. Inspect `target/installation/plugins` in the repository module. It must contain the companion,
   a compatible core 1.x bundle, and compatible Eclipse OCL 1.x bundles at or above 1.24.0. Inspect
   `target/source-installation/plugins` too; it must contain
   `io.github.lorenzobettini.emfmodelgenerator.ocl.source`.
6. Test the generated repository in a clean Eclipse 2026-06-compatible installation using the four
   repositories listed in the README.
7. Review third-party licenses and notices, then tag the verified commit.
8. Publish the contents of
   `releng/io.github.lorenzobettini.emfmodelgenerator.ocl.repository/target/repository/` to the
   companion's separate update-site hosting. Preserve released versions rather than replacing them.
9. Verify the published companion site with p2 Director, then publish release notes and the
   repository ZIP generated beside `target/repository/`.

The core update site and the companion update site are independent: the former supplies the core
bundle; the latter supplies only the optional OCL validation companion. Eclipse and Eclipse OCL
remain referenced upstream repositories and are not republished in the companion repository.

The companion repository publishes both the runtime `EMF Model Generator OCL` feature and the
separately installable `EMF Model Generator OCL Source` feature. The latter contains only this
project's generated bundle source; it does not republish source from external dependencies.
