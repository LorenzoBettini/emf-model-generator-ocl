/*******************************************************************************
 * Copyright (c) 2026 Lorenzo Bettini and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.github.lorenzobettini.emfmodelgenerator.ocl.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.EMFValidationKind;
import io.github.lorenzobettini.emfmodelgenerator.EMFValidationResult;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;

/** Controlled, explicitly configured candidates for the Task 10 evaluation. */
class OCLValidationExperimentTest {

	private static final String METAMODEL = "/model/example/education.ecore";
	private static final String EXPERIMENT_PROPERTY = "ocl.validation.experiment";
	private static final String BASELINE = "baseline-default";
	private static final String CUSTOMIZED = "configured-success";
	private static final String BROAD = "broad-0-24";
	private static final String RESTRICTED = "restricted-21-24";
	private static final String WARM_UP = "warm-up-discarded";
	private static final String INVARIANT = "AtLeast21";
	private static final String COMMAND = "./mvnw verify -am "
			+ "-pl :io.github.lorenzobettini.emfmodelgenerator.ocl.tests "
			+ "-Dtycho.localArtifacts=ignore -Dtest=OCLValidationExperimentTest "
			+ "-Dtycho.testArgLine=-Docl.validation.experiment=true";

	@TempDir
	Path temporaryDirectory;

	private int candidateSequence;

	@Test
	void selectedConfigurationsHaveRepeatableOutcomesAndIndependentState() throws Exception {
		Path metamodelFile = copyMetamodel();

		List<Observation> first = runScenarios(metamodelFile);
		List<Observation> second = runScenarios(metamodelFile);

		assertExpectedOutcomes(first);
		assertExpectedOutcomes(second);
		assertEquals(first.stream().map(Observation::semantic).toList(),
				second.stream().map(Observation::semantic).toList());
	}

	@Test
	void writeOptInMeasurements() throws Exception {
		assumeTrue(Boolean.getBoolean(EXPERIMENT_PROPERTY),
				() -> "Enable with -D" + EXPERIMENT_PROPERTY + "=true");
		Path metamodelFile = copyMetamodel();
		warmUp(metamodelFile);

		List<Observation> observations = runScenarios(metamodelFile);
		assertExpectedOutcomes(observations);

		Path outputDirectory = Path.of("target", "ocl-validation-experiment");
		Files.createDirectories(outputDirectory);
		Path csv = outputDirectory.resolve("observations.csv");
		Path summary = outputDirectory.resolve("summary.txt");
		Files.writeString(csv, csv(observations), StandardCharsets.UTF_8);
		String summaryText = summary(observations, csv, summary);
		Files.writeString(summary, summaryText, StandardCharsets.UTF_8);
		System.out.println(summaryText);
	}

	private List<Observation> runScenarios(Path metamodelFile) throws IOException {
		Set<ResourceSet> resourceSets = Collections.newSetFromMap(new IdentityHashMap<>());
		var observations = new ArrayList<Observation>();
		observations.add(runCandidate(metamodelFile, resourceSets, BASELINE, null));
		observations.add(runCandidate(metamodelFile, resourceSets, CUSTOMIZED, 21));
		for (int age = 0; age <= 24; age++) {
			observations.add(runCandidate(metamodelFile, resourceSets, BROAD, age));
		}
		for (int age = 21; age <= 24; age++) {
			observations.add(runCandidate(metamodelFile, resourceSets, RESTRICTED, age));
		}
		assertEquals(observations.size(), resourceSets.size());
		return List.copyOf(observations);
	}

	private void warmUp(Path metamodelFile) throws IOException {
		Set<ResourceSet> resourceSets = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int age : List.of(0, 21, 0, 21)) {
			runCandidate(metamodelFile, resourceSets, WARM_UP, age);
		}
		assertEquals(4, resourceSets.size());
	}

	private Observation runCandidate(Path metamodelFile, Set<ResourceSet> resourceSets,
			String scenario, Integer selectedAge) throws IOException {
		ResourceSet resourceSet = configuredResourceSet();
		assertTrue(resourceSets.add(resourceSet), "Every candidate needs a fresh ResourceSet");
		assertTrue(resourceSet.getResources().isEmpty());
		var generator = new EMFModelGenerator(resourceSet);
		generator.setOutputDirectory(temporaryDirectory.resolve(
				"generated-" + candidateSequence++).toString());
		EPackage ePackage = generator.loadEcoreModel(metamodelFile.toString());
		EClass schoolClass = (EClass) ePackage.getEClassifier("School");
		EClass studentClass = (EClass) ePackage.getEClassifier("Student");
		EAttribute ageAttribute = (EAttribute) studentClass.getEStructuralFeature("age");
		if (selectedAge != null) {
			generator.getInstancePopulator().functionForAttribute(
					ageAttribute, owner -> selectedAge);
		}

		try {
			long combinedStarted = System.nanoTime();
			long generationStarted = combinedStarted;
			EObject school = generator.generateFrom(schoolClass);
			long generationFinished = System.nanoTime();
			long validationStarted = generationFinished;
			int initialAdapters = resourceSet.eAdapters().size();
			assertFalse(resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().containsKey("essentialocl"));
			OCLModelValidator validator = OCLModelValidator.forOCLinEcore(resourceSet);
			EMFValidationResult result;
			long validationFinished;
			try {
				result = validator.validate(school);
				validationFinished = System.nanoTime();
			} finally {
				validator.close();
			}

			List<Integer> expectedAges = selectedAge == null
					? List.of(20, 21)
					: List.of(selectedAge, selectedAge);
			assertEquals(expectedAges, ages(school));
			assertSame(resourceSet, school.eResource().getResourceSet());
			assertEquals(2, resourceSet.getResources().size());
			assertEquals(1, school.eResource().getContents().size());
			assertEquals(1, modelRootCount(resourceSet));
			assertEquals(3, objectCount(school));
			assertFalse(resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().containsKey("essentialocl"));
			assertEquals(initialAdapters, resourceSet.eAdapters().size());
			assertEquals(expectedAges, ages(school), "Validation must not change candidate values");

			String invariantDiagnostic = invariantDiagnostic(result);
			return new Observation(scenario, selectedAge, result.kind(), List.copyOf(expectedAges),
					1, objectCount(school), result.rejectedSeverity(), invariantDiagnostic,
					generationFinished - generationStarted,
					validationFinished - validationStarted,
					validationFinished - combinedStarted);
		} finally {
			String nsURI = ePackage.getNsURI();
			generator.unloadEcoreModels();
			for (Resource resource : List.copyOf(resourceSet.getResources())) {
				resource.unload();
			}
			resourceSet.getResources().clear();
			assertTrue(resourceSet.getResources().isEmpty());
			assertFalse(EPackage.Registry.INSTANCE.containsKey(nsURI));
		}
	}

	private static void assertExpectedOutcomes(List<Observation> observations) {
		assertEquals(31, observations.size());
		Observation baseline = only(observations, BASELINE);
		assertEquals(List.of(20, 21), baseline.generatedAges());
		assertEquals(EMFValidationKind.VALIDATION_FAILURE, baseline.validity());
		assertTrue(baseline.invariantDiagnostic().contains(INVARIANT));

		Observation customized = only(observations, CUSTOMIZED);
		assertEquals(List.of(21, 21), customized.generatedAges());
		assertEquals(EMFValidationKind.VALID, customized.validity());

		List<Observation> broad = scenario(observations, BROAD);
		assertEquals(range(0, 24), broad.stream().map(Observation::selectedAge).toList());
		assertCounts(broad, 4, 21);
		List<Observation> restricted = scenario(observations, RESTRICTED);
		assertEquals(range(21, 24), restricted.stream().map(Observation::selectedAge).toList());
		assertCounts(restricted, 4, 0);

		assertTrue(observations.stream()
				.noneMatch(observation -> observation.validity()
						== EMFValidationKind.VALIDATOR_FAILURE));
		assertTrue(observations.stream()
				.allMatch(observation -> observation.roots() == 1
						&& observation.objects() == 3
						&& observation.rejectedSeverity() == Diagnostic.WARNING));
	}

	private static void assertCounts(List<Observation> observations, long valid, long invalid) {
		assertEquals(valid, count(observations, EMFValidationKind.VALID));
		assertEquals(invalid, count(observations, EMFValidationKind.VALIDATION_FAILURE));
		assertEquals(0, count(observations, EMFValidationKind.VALIDATOR_FAILURE));
		for (Observation observation : observations) {
			EMFValidationKind expected = observation.selectedAge() >= 21
					? EMFValidationKind.VALID
					: EMFValidationKind.VALIDATION_FAILURE;
			assertEquals(expected, observation.validity());
			assertEquals(List.of(observation.selectedAge(), observation.selectedAge()),
					observation.generatedAges());
			if (expected == EMFValidationKind.VALID) {
				assertTrue(observation.invariantDiagnostic().isEmpty());
			} else {
				assertTrue(observation.invariantDiagnostic().contains(INVARIANT));
			}
		}
	}

	private static Observation only(List<Observation> observations, String scenario) {
		List<Observation> selected = scenario(observations, scenario);
		assertEquals(1, selected.size());
		return selected.getFirst();
	}

	private static List<Observation> scenario(List<Observation> observations, String scenario) {
		return observations.stream()
				.filter(observation -> scenario.equals(observation.scenario()))
				.toList();
	}

	private static long count(List<Observation> observations, EMFValidationKind kind) {
		return observations.stream()
				.filter(observation -> observation.validity() == kind)
				.count();
	}

	private static List<Integer> range(int first, int last) {
		var result = new ArrayList<Integer>();
		for (int value = first; value <= last; value++) {
			result.add(value);
		}
		return List.copyOf(result);
	}

	private static String csv(List<Observation> observations) {
		var csv = new StringBuilder("scenario,configuration,age,validity,roots,objects,")
				.append("generation_ns,ocl_setup_and_validation_ns,combined_ns,")
				.append("invariant_diagnostic\n");
		for (Observation observation : observations) {
			csv.append(csvField(observation.scenario())).append(',')
					.append(csvField(observation.configuration())).append(',')
					.append(observation.selectedAge() == null ? "" : observation.selectedAge())
					.append(',').append(observation.validity()).append(',')
					.append(observation.roots()).append(',')
					.append(observation.objects()).append(',')
					.append(observation.generationNanos()).append(',')
					.append(observation.oclSetupAndValidationNanos()).append(',')
					.append(observation.combinedNanos()).append(',')
					.append(csvField(observation.invariantDiagnostic())).append('\n');
		}
		return csv.toString();
	}

	private static String summary(List<Observation> observations, Path csv, Path summary) {
		var text = new StringBuilder();
		text.append("OCL validation controlled experiment\n")
				.append("====================================\n")
				.append("Output CSV: ").append(csv.toAbsolutePath()).append('\n')
				.append("Output summary: ").append(summary.toAbsolutePath()).append('\n')
				.append("Command: ").append(COMMAND).append("\n\n")
				.append("Environment\n")
				.append("-----------\n")
				.append("Java: ").append(System.getProperty("java.version")).append(' ')
				.append(System.getProperty("java.vm.name")).append('\n')
				.append("OS: ").append(System.getProperty("os.name")).append(' ')
				.append(System.getProperty("os.version")).append(' ')
				.append(System.getProperty("os.arch")).append('\n')
				.append("Available processors: ")
				.append(Runtime.getRuntime().availableProcessors()).append('\n')
				.append("Method\n")
				.append("------\n")
				.append("Four fresh candidates (ages 0, 21, 0, 21) warm the JVM and are ")
				.append("discarded.\n")
				.append("Each measured candidate uses a fresh generator, ResourceSet, and OCL ")
				.append("validator.\n")
				.append("generation_ns covers generateFrom only; fixture loading and caller ")
				.append("configuration are excluded.\n")
				.append("ocl_setup_and_validation_ns covers validator construction through its ")
				.append("first validation, including lazy compilation; close follows the timer.\n")
				.append("combined_ns is the continuous interval from generateFrom entry through ")
				.append("first-validation completion.\n")
				.append("All times are nanoseconds. Candidate order is baseline, configured age ")
				.append("21, ages 0..24, then ages 21..24.\n\n")
				.append("Observed outcomes\n")
				.append("-----------------\n");
		appendSingle(text, only(observations, BASELINE));
		appendSingle(text, only(observations, CUSTOMIZED));
		appendSweep(text, BROAD, scenario(observations, BROAD));
		appendSweep(text, RESTRICTED, scenario(observations, RESTRICTED));
		text.append('\n')
				.append("Interpretation and limitations\n")
				.append("------------------------------\n")
				.append("These are outcomes for deliberately selected, finite configurations of ")
				.append("one three-object fixture, not probabilities or a general benchmark.\n")
				.append("The core customization supplies each age during generation. The OCL ")
				.append("validation companion observes completed candidates; it does not retry, ")
				.append("search, select, or repair values.\n")
				.append("Timing includes a new OCL session and first validation for every measured ")
				.append("candidate. It is not Task 07's warmed, reused-validator measurement.\n")
				.append("Times are environment- and load-sensitive observations without performance ")
				.append("thresholds or general guarantees.\n");
		return text.toString();
	}

	private static void appendSingle(StringBuilder text, Observation observation) {
		text.append(observation.scenario()).append(": configuration=")
				.append(observation.configuration()).append(", outcome=")
				.append(observation.validity()).append(", generation_ms=")
				.append(milliseconds(observation.generationNanos()))
				.append(", ocl_setup_and_validation_ms=")
				.append(milliseconds(observation.oclSetupAndValidationNanos()))
				.append(", combined_ms=")
				.append(milliseconds(observation.combinedNanos())).append('\n');
	}

	private static void appendSweep(StringBuilder text, String name,
			List<Observation> observations) {
		long accepted = count(observations, EMFValidationKind.VALID);
		long rejected = count(observations, EMFValidationKind.VALIDATION_FAILURE);
		long failures = count(observations, EMFValidationKind.VALIDATOR_FAILURE);
		text.append(name).append(": accepted=").append(accepted).append('/')
				.append(observations.size()).append(" (")
				.append(percent(accepted, observations.size())).append("%), rejected=")
				.append(rejected).append('/').append(observations.size()).append(" (")
				.append(percent(rejected, observations.size())).append("%), validator_failures=")
				.append(failures).append(", median_generation_ms=")
				.append(milliseconds(median(observations, Observation::generationNanos)))
				.append(", median_ocl_setup_and_validation_ms=")
				.append(milliseconds(median(
						observations, Observation::oclSetupAndValidationNanos)))
				.append(", median_combined_ms=")
				.append(milliseconds(median(observations, Observation::combinedNanos)))
				.append('\n');
	}

	private static long median(List<Observation> observations, Timing timing) {
		List<Long> values = observations.stream().map(timing::nanos).sorted().toList();
		int middle = values.size() / 2;
		if (values.size() % 2 == 1) {
			return values.get(middle);
		}
		return (values.get(middle - 1) + values.get(middle)) / 2;
	}

	private static String milliseconds(long nanoseconds) {
		return String.format(Locale.ROOT, "%.3f", nanoseconds / 1_000_000.0);
	}

	private static String percent(long count, int total) {
		return String.format(Locale.ROOT, "%.1f", count * 100.0 / total);
	}

	private static String csvField(String value) {
		return '"' + value.replace("\"", "\"\"") + '"';
	}

	private Path copyMetamodel() throws IOException {
		Path destination = temporaryDirectory.resolve("education.ecore");
		try (InputStream source = getClass().getResourceAsStream(METAMODEL)) {
			assertNotNull(source, "Missing test resource " + METAMODEL);
			Files.copy(source, destination);
		}
		return destination;
	}

	private static ResourceSet configuredResourceSet() {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("ecore", new EcoreResourceFactoryImpl());
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("xmi", new XMIResourceFactoryImpl());
		return resourceSet;
	}

	@SuppressWarnings("unchecked")
	private static List<Integer> ages(EObject school) {
		List<EObject> students = (List<EObject>) school.eGet(
				school.eClass().getEStructuralFeature("students"));
		assertEquals(2, students.size());
		return students.stream()
				.map(student -> (Integer) student.eGet(
						student.eClass().getEStructuralFeature("age")))
				.toList();
	}

	private static int modelRootCount(ResourceSet resourceSet) {
		return resourceSet.getResources().stream()
				.filter(resource -> !"ecore".equals(resource.getURI().fileExtension()))
				.mapToInt(resource -> resource.getContents().size())
				.sum();
	}

	private static int objectCount(EObject root) {
		int count = 0;
		var contents = EcoreUtil.getAllContents(List.of(root));
		while (contents.hasNext()) {
			contents.next();
			count++;
		}
		return count;
	}

	private static String invariantDiagnostic(EMFValidationResult result) {
		return result.flattenedDiagnostics().stream()
				.map(Diagnostic::getMessage)
				.filter(message -> message != null && message.contains(INVARIANT))
				.findFirst()
				.orElse("");
	}

	@FunctionalInterface
	private interface Timing {
		long nanos(Observation observation);
	}

	private record Observation(
			String scenario,
			Integer selectedAge,
			EMFValidationKind validity,
			List<Integer> generatedAges,
			int roots,
			int objects,
			int rejectedSeverity,
			String invariantDiagnostic,
			long generationNanos,
			long oclSetupAndValidationNanos,
			long combinedNanos) {

		String configuration() {
			return selectedAge == null ? "default" : "age=" + selectedAge;
		}

		SemanticObservation semantic() {
			return new SemanticObservation(scenario, selectedAge, validity, generatedAges,
					roots, objects, rejectedSeverity);
		}
	}

	private record SemanticObservation(
			String scenario,
			Integer selectedAge,
			EMFValidationKind validity,
			List<Integer> generatedAges,
			int roots,
			int objects,
			int rejectedSeverity) {
	}
}
