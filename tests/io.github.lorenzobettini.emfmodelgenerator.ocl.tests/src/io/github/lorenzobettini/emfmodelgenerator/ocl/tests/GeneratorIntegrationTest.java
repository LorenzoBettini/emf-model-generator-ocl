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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLValidationException;

class GeneratorIntegrationTest {

	private static final String METAMODEL = "/model/integration/education.ecore";
	private static final String COMPLETE_OCL_METAMODEL = "/model/completeocl/education.ecore";
	private static final String COMPLETE_OCL_CONSTRAINTS =
			"/model/completeocl/constraints/education.ocl";

	@TempDir
	Path temporaryDirectory;

	@Test
	void oneGeneratedRootIsValidatedAfterGeneration() throws Exception {
		try (var context = generatorContext("one-root")) {
			EObject school = context.generator().generateFrom(context.schoolClass());

			try (var validator = OCLModelValidator.forOCLinEcore(context.resourceSet())) {
				var result = validator.validate(school);

				assertTrue(result.isValid(), () -> messages(result.diagnostic()));
				assertSame(context.resourceSet(), school.eResource().getResourceSet());
			}
		}
	}

	@Test
	void generatorOwnedResourceSetCanBeRetrievedAndUsedForValidation() throws Exception {
		Path directory = temporaryDirectory.resolve("generator-owned-resource-set");
		Path metamodelFile = copyMetamodel(directory);
		var generator = new EMFModelGenerator();
		generator.setOutputDirectory(directory.resolve("generated").toString());
		EPackage ePackage = generator.loadEcoreModel(metamodelFile.toString());
		try {
			EObject school = generator.generateFrom((EClass) ePackage.getEClassifier("School"));
			ResourceSet resourceSet = generator.getResourceSet();
			Object wildcardFactory = resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().get("*");

			assertSame(resourceSet, school.eResource().getResourceSet());
			assertNotNull(resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().get("xmi"));
			assertNotNull(wildcardFactory);
			assertFalse(resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().containsKey("essentialocl"));
			try (var validator = OCLModelValidator.forOCLinEcore(resourceSet)) {
				var result = validator.validate(school);
				assertTrue(result.isValid(), () -> messages(result.diagnostic()));
			}
			assertFalse(resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().containsKey("essentialocl"));
			assertSame(wildcardFactory, resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().get("*"));
		} finally {
			generator.unloadEcoreModels();
		}
		assertFalse(EPackage.Registry.INSTANCE.containsKey(ePackage.getNsURI()));
	}

	@Test
	void generatorOwnedResourceSetCanLoadExternalCompleteOCL() throws Exception {
		Path directory = temporaryDirectory.resolve("generator-owned-complete-ocl");
		Path metamodelFile = copyResource(COMPLETE_OCL_METAMODEL,
				directory.resolve("education.ecore"));
		Path constraintsFile = copyResource(COMPLETE_OCL_CONSTRAINTS,
				directory.resolve("constraints/education.ocl"));
		var generator = new EMFModelGenerator();
		EPackage ePackage = generator.loadEcoreModel(metamodelFile.toString());
		try {
			EObject student = generator.generateFrom(
					(EClass) ePackage.getEClassifier("Student"));
			student.eSet(student.eClass().getEStructuralFeature("name"), "Alice");
			student.eSet(student.eClass().getEStructuralFeature("age"), 17);
			ResourceSet resourceSet = generator.getResourceSet();
			Object wildcardFactory = resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().get("*");

			assertFalse(resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().containsKey("ocl"));
			try (var validator = OCLModelValidator.forCompleteOCL(resourceSet,
					URI.createFileURI(constraintsFile.toString()))) {
				var result = validator.validate(student);

				assertFalse(result.isValid());
				assertTrue(messages(result.diagnostic()).contains("Adult"));
			}
			assertFalse(resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().containsKey("ocl"));
			assertSame(wildcardFactory, resourceSet.getResourceFactoryRegistry()
					.getExtensionToFactoryMap().get("*"));
		} finally {
			generator.unloadEcoreModels();
		}
	}

	@Test
	void invalidGeneratedOutputStopsBeforeSaveAndIsNotChanged() throws Exception {
		try (var context = generatorContext("invalid")) {
			EObject school = context.generator().generateFrom(context.schoolClass());
			EObject student = students(school).getFirst();
			EStructuralFeature age = student.eClass().getEStructuralFeature("age");
			student.eSet(age, 17);

			try (var validator = OCLModelValidator.forOCLinEcore(context.resourceSet())) {
				var exception = assertThrows(OCLValidationException.class,
						() -> validateThenSave(context.generator(), validator, school));

				assertFalse(exception.getResult().isValid());
				assertTrue(messages(exception.getResult().diagnostic()).contains("Adult"));
				assertEquals(17, student.eGet(age));
				assertFalse(Files.exists(context.outputDirectory()));
			}
		}
	}

	@Test
	void severalRootsGeneratedTogetherCanReferenceAcrossResources() throws Exception {
		try (var context = generatorContext("several-roots")) {
			List<EObject> schools = context.generator().generateFromSeveral(
					context.schoolClass(), context.schoolClass());
			EObject first = schools.get(0);
			EObject second = schools.get(1);
			EStructuralFeature partner = context.schoolClass().getEStructuralFeature("partner");
			EStructuralFeature name = context.schoolClass().getEStructuralFeature("name");

			assertNotSame(first.eResource(), second.eResource());
			assertSame(second, first.eGet(partner));
			assertSame(first, second.eGet(partner));
			assertNotSame(first.eResource(), ((EObject) first.eGet(partner)).eResource());

			try (var validator = OCLModelValidator.forOCLinEcore(context.resourceSet())) {
				Object generatedName = second.eGet(name);
				second.eSet(name, "");
				var crossResourceFailure = validator.validate(first);
				assertFalse(crossResourceFailure.isValid());
				assertTrue(messages(crossResourceFailure.diagnostic()).contains("PartnerNamed"));
				second.eSet(name, generatedName);

				var result = validator.validateAll(schools);

				assertTrue(result.isValid(), () -> messages(result.diagnostic()));
				assertEquals(2, result.diagnostic().getChildren().size());
			}
		}
	}

	@Test
	void validGeneratedOutputCanBeSavedReloadedAndRevalidated() throws Exception {
		Path modelFile;
		Path metamodelFile;
		try (var context = generatorContext("round-trip")) {
			EObject school = context.generator().generateFrom(context.schoolClass());
			modelFile = Path.of(school.eResource().getURI().toFileString());
			metamodelFile = context.metamodelFile();

			try (var validator = OCLModelValidator.forOCLinEcore(context.resourceSet())) {
				validateThenSave(context.generator(), validator, school);
			}
			assertTrue(Files.isRegularFile(modelFile));
		}

		ResourceSet reloadedResourceSet = configuredResourceSet();
		EPackage reloadedPackage = loadPackage(reloadedResourceSet, metamodelFile);
		Resource reloadedResource = reloadedResourceSet.getResource(
				URI.createFileURI(modelFile.toString()), true);
		EObject reloadedSchool = reloadedResource.getContents().getFirst();

		try (var validator = OCLModelValidator.forOCLinEcore(reloadedResourceSet)) {
			var result = validator.validate(reloadedSchool);

			assertTrue(result.isValid(), () -> messages(result.diagnostic()));
			assertSame(reloadedPackage, reloadedSchool.eClass().getEPackage());
		}
	}

	private GeneratorContext generatorContext(String name) throws IOException {
		Path directory = temporaryDirectory.resolve(name);
		Path metamodelFile = copyMetamodel(directory);
		ResourceSet resourceSet = configuredResourceSet();
		var generator = new EMFModelGenerator(resourceSet);
		Path outputDirectory = directory.resolve("generated");
		generator.setOutputDirectory(outputDirectory.toString());
		EPackage ePackage = generator.loadEcoreModel(metamodelFile.toString());
		EClass schoolClass = (EClass) ePackage.getEClassifier("School");
		return new GeneratorContext(resourceSet, generator, schoolClass, ePackage.getNsURI(),
				metamodelFile, outputDirectory);
	}

	private Path copyMetamodel(Path directory) throws IOException {
		Files.createDirectories(directory);
		return copyResource(METAMODEL, directory.resolve("education.ecore"));
	}

	private Path copyResource(String resourcePath, Path destination) throws IOException {
		Files.createDirectories(destination.getParent());
		try (InputStream source = getClass().getResourceAsStream(resourcePath)) {
			assertNotNull(source, "Missing test resource " + resourcePath);
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
		Resource resource = resourceSet.getResource(
				URI.createFileURI(metamodelFile.toString()), true);
		EPackage ePackage = (EPackage) resource.getContents().getFirst();
		resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
		return ePackage;
	}

	private static void validateThenSave(EMFModelGenerator generator,
			OCLModelValidator validator, EObject root) throws IOException {
		validator.validateOrThrow(root);
		generator.save();
	}

	@SuppressWarnings("unchecked")
	private static List<EObject> students(EObject school) {
		return (List<EObject>) school.eGet(school.eClass().getEStructuralFeature("students"));
	}

	private static String messages(Diagnostic diagnostic) {
		var result = new StringBuilder(diagnostic.getMessage());
		for (Diagnostic child : diagnostic.getChildren()) {
			result.append('\n').append(messages(child));
		}
		return result.toString();
	}

	private record GeneratorContext(
			ResourceSet resourceSet,
			EMFModelGenerator generator,
			EClass schoolClass,
			String packageNsURI,
			Path metamodelFile,
			Path outputDirectory) implements AutoCloseable {

		@Override
		public void close() {
			generator.unloadEcoreModels();
			assertFalse(EPackage.Registry.INSTANCE.containsKey(packageNsURI));
		}
	}
}
