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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
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
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;

/** Executable example for the SoSyM revision. */
class ExampleTest {

	private static final String METAMODEL = "/model/example/education.ecore";
	private static final int REPETITIONS = 31;

	@TempDir
	Path temporaryDirectory;

	@Test
	void generationValidationCustomizationAndRoundTrip() throws Exception {
		Path metamodelFile = copyMetamodel();
		ResourceSet resourceSet = configuredResourceSet();
		var generator = generator(resourceSet, metamodelFile, "generated");
		EPackage ePackage = packageOf(generator);
		EClass schoolClass = (EClass) ePackage.getEClassifier("School");
		EClass studentClass = (EClass) ePackage.getEClassifier("Student");
		EAttribute age = (EAttribute) studentClass.getEStructuralFeature("age");

		try {
			EObject defaultSchool = generator.generateFrom(schoolClass);
			long setupStarted = System.nanoTime();
			try (var validator = OCLModelValidator.forOCLinEcore(resourceSet)) {
				var defaultResult = validator.validate(defaultSchool);
				long setupAndFirstValidationNanos = System.nanoTime() - setupStarted;

				assertFalse(defaultResult.isValid());
				assertTrue(messages(defaultResult.diagnostic()).contains("AtLeast21"));
				assertEquals(List.of(20, 21), ages(defaultSchool));

				generator.getInstancePopulator().functionForAttribute(age, owner -> 21);
				EObject compliantSchool = generator.generateFrom(schoolClass);
				assertEquals(List.of(21, 21), ages(compliantSchool));
				assertTrue(validator.validate(compliantSchool).isValid());

				List<Long> validationNanos = measureRepeatedValidation(
						validator, compliantSchool);
				List<Long> generateAndValidateNanos = measureGenerateAndValidate(
						generator, validator, schoolClass);

				Path modelFile = Path.of(compliantSchool.eResource().getURI().toFileString());
				validator.validateOrThrow(compliantSchool);
				generator.save();
				assertTrue(Files.isRegularFile(modelFile));

				int objectCount = objectCount(compliantSchool);
				assertEquals(3, objectCount);
				printExample(setupAndFirstValidationNanos, validationNanos,
						generateAndValidateNanos, objectCount);

				assertReloadedModelIsValid(metamodelFile, modelFile);
			}
		} finally {
			generator.unloadEcoreModels();
		}
	}

	private List<Long> measureRepeatedValidation(OCLModelValidator validator, EObject root) {
		var measurements = new ArrayList<Long>();
		for (int i = 0; i < REPETITIONS; i++) {
			long started = System.nanoTime();
			var result = validator.validate(root);
			measurements.add(System.nanoTime() - started);
			assertTrue(result.isValid());
		}
		return measurements;
	}

	private List<Long> measureGenerateAndValidate(EMFModelGenerator generator,
			OCLModelValidator validator, EClass schoolClass) {
		var measurements = new ArrayList<Long>();
		for (int i = 0; i < REPETITIONS; i++) {
			long started = System.nanoTime();
			EObject school = generator.generateFrom(schoolClass);
			var result = validator.validate(school);
			measurements.add(System.nanoTime() - started);
			assertTrue(result.isValid());
			assertEquals(3, objectCount(school));
		}
		return measurements;
	}

	private void assertReloadedModelIsValid(Path metamodelFile, Path modelFile) {
		ResourceSet reloaded = configuredResourceSet();
		EPackage ePackage = loadPackage(reloaded, metamodelFile);
		Resource resource = reloaded.getResource(URI.createFileURI(modelFile.toString()), true);
		EObject root = resource.getContents().getFirst();

		try (var validator = OCLModelValidator.forOCLinEcore(reloaded)) {
			assertTrue(validator.validate(root).isValid());
			assertEquals(3, objectCount(root));
			assertEquals(ePackage, root.eClass().getEPackage());
		}
	}

	private EMFModelGenerator generator(ResourceSet resourceSet, Path metamodelFile,
			String outputName) throws IOException {
		var generator = new EMFModelGenerator(resourceSet);
		generator.setOutputDirectory(temporaryDirectory.resolve(outputName).toString());
		generator.loadEcoreModel(metamodelFile.toString());
		return generator;
	}

	private static EPackage packageOf(EMFModelGenerator generator) {
		return (EPackage) generator.getResourceSet().getResources().stream()
				.filter(resource -> "ecore".equals(resource.getURI().fileExtension()))
				.findFirst().orElseThrow().getContents().getFirst();
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

	private static EPackage loadPackage(ResourceSet resourceSet, Path metamodelFile) {
		Resource resource = resourceSet.getResource(URI.createFileURI(metamodelFile.toString()), true);
		EPackage ePackage = (EPackage) resource.getContents().getFirst();
		resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
		return ePackage;
	}

	@SuppressWarnings("unchecked")
	private static List<Integer> ages(EObject school) {
		List<EObject> students = (List<EObject>) school.eGet(
				school.eClass().getEStructuralFeature("students"));
		return students.stream()
				.map(student -> (Integer) student.eGet(
						student.eClass().getEStructuralFeature("age")))
				.toList();
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

	private static String messages(Diagnostic diagnostic) {
		var result = new StringBuilder(diagnostic.getMessage());
		for (Diagnostic child : diagnostic.getChildren()) {
			result.append('\n').append(messages(child));
		}
		return result.toString();
	}

	private static void printExample(long setupNanos, List<Long> validationNanos,
			List<Long> generateAndValidateNanos, int objectCount) {
		System.out.printf("EXAMPLE setup_and_first_validation_ms=%.3f%n",
				milliseconds(setupNanos));
		System.out.printf("EXAMPLE repeated_validation_median_ms=%.3f repetitions=%d%n",
				milliseconds(median(validationNanos)), validationNanos.size());
		System.out.printf("EXAMPLE generate_plus_validate_median_ms=%.3f repetitions=%d%n",
				milliseconds(median(generateAndValidateNanos)), generateAndValidateNanos.size());
		System.out.printf("EXAMPLE model_roots=1 validated_objects=%d%n", objectCount);
	}

	private static long median(List<Long> measurements) {
		var sorted = new ArrayList<>(measurements);
		Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}

	private static double milliseconds(long nanoseconds) {
		return nanoseconds / 1_000_000.0;
	}
}
