/*******************************************************************************
 * Copyright (c) 2026 Lorenzo Bettini and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.github.lorenzobettini.emfmodelgenerator.ocl.tests.fixture;

import java.net.URL;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.ocl.pivot.utilities.PivotConstants;

/** Real dynamic-Ecore fixture loaded from the bundled education metamodel. */
public final class EducationFixture {

	private static final String EDUCATION_ECORE = "/model/education.ecore";

	private final ResourceSet resourceSet;
	private final EPackage ePackage;
	private final EClass schoolClass;
	private final EClass studentClass;
	private final EReference studentsReference;
	private final EAttribute nameAttribute;
	private final EAttribute ageAttribute;
	private final EAttribute identifierAttribute;

	/** Loads a fresh dynamic education package in a fresh {@link ResourceSet}. */
	public EducationFixture() {
		this(EDUCATION_ECORE);
	}

	/**
	 * Loads a fresh dynamic education package from {@code modelPath} in a fresh resource set.
	 *
	 * @param modelPath absolute bundle resource path of the Ecore model
	 */
	public EducationFixture(String modelPath) {
		resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("ecore", new EcoreResourceFactoryImpl());
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put("xmi", new XMIResourceFactoryImpl());
		URL model = EducationFixture.class.getResource(modelPath);
		if (model == null) {
			throw new IllegalStateException("Missing test metamodel " + modelPath);
		}
		Resource resource = resourceSet.getResource(URI.createURI(model.toExternalForm()), true);
		ePackage = (EPackage) resource.getContents().getFirst();
		resourceSet.getPackageRegistry().put(ePackage.getNsURI(), ePackage);
		schoolClass = eClass("School");
		studentClass = eClass("Student");
		studentsReference = (EReference) schoolClass.getEStructuralFeature("students");
		nameAttribute = (EAttribute) studentClass.getEStructuralFeature("name");
		ageAttribute = (EAttribute) studentClass.getEStructuralFeature("age");
		identifierAttribute = (EAttribute) studentClass.getEStructuralFeature("identifier");
	}

	/** @return this fixture's caller-owned resource set */
	public ResourceSet resourceSet() {
		return resourceSet;
	}

	/** @return the dynamically loaded education package */
	public EPackage ePackage() {
		return ePackage;
	}

	/** Creates and attaches an empty school root to an in-memory model resource. */
	public EObject school(String resourceName) {
		EObject school = ePackage.getEFactoryInstance().create(schoolClass);
		resourceSet.createResource(URI.createURI("memory:/" + resourceName + ".xmi"))
				.getContents().add(school);
		return school;
	}

	/** Creates a student and contains it in {@code school}. */
	public EObject student(EObject school, String name, int age, String identifier) {
		EObject student = ePackage.getEFactoryInstance().create(studentClass);
		student.eSet(nameAttribute, name);
		student.eSet(ageAttribute, age);
		student.eSet(identifierAttribute, identifier);
		students(school).add(student);
		return student;
	}

	/** Replaces one embedded Student invariant expression for failure-path tests. */
	public void studentConstraint(String constraintName, String expression) {
		EcoreUtil.setAnnotation(studentClass, PivotConstants.OCL_DELEGATE_URI_PIVOT,
				constraintName, expression);
	}

	@SuppressWarnings("unchecked")
	private EList<EObject> students(EObject school) {
		return (EList<EObject>) school.eGet(studentsReference);
	}

	private EClass eClass(String name) {
		return (EClass) ePackage.getEClassifier(name);
	}
}
