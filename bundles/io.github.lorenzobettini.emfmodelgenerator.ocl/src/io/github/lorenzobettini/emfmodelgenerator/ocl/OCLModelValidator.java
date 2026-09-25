/*******************************************************************************
 * Copyright (c) 2026 Lorenzo Bettini and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.github.lorenzobettini.emfmodelgenerator.ocl;

import java.util.Collection;
import java.util.Objects;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

import io.github.lorenzobettini.emfmodelgenerator.ocl.internal.CompleteOCLModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.internal.OCLinEcoreModelValidator;

/**
 * Validates complete EMF object graphs after generation against configured OCL constraints.
 * Implementations validate candidates but neither direct generation nor repair invalid candidates.
 */
public interface OCLModelValidator extends AutoCloseable {

	/**
	 * Creates a validation session for Pivot OCL constraints embedded in Ecore annotations.
	 *
	 * @param resourceSet the caller's resource set containing metamodels and generated models
	 * @return a new headless OCLinEcore validation session
	 * @throws NullPointerException if {@code resourceSet} is {@code null}
	 */
	static OCLModelValidator forOCLinEcore(ResourceSet resourceSet) {
		return new OCLinEcoreModelValidator(resourceSet);
	}

	/**
	 * Creates a validation session for constraints in an external Complete OCL document.
	 * The document is resolved in the caller's resource set, so its relative imports use the
	 * document URI as their base. Constraints complement the imported EPackages in memory and do
	 * not modify their Ecore annotations.
	 *
	 * @param resourceSet the caller's resource set containing metamodels and generated models
	 * @param oclURI URI of the external Complete OCL document
	 * @return a new headless Complete OCL validation session
	 * @throws NullPointerException if either argument is {@code null}
	 */
	static OCLModelValidator forCompleteOCL(ResourceSet resourceSet, URI oclURI) {
		return new CompleteOCLModelValidator(resourceSet, oclURI);
	}

	/**
	 * Validates one complete generated root.
	 *
	 * @param root root of the object graph to validate
	 * @return the structured validation result
	 * @throws NullPointerException if {@code root} is {@code null}
	 * @throws IllegalStateException if this validator has been closed
	 */
	OCLValidationResult validate(EObject root);

	/**
	 * Validates several complete generated roots as one operation.
	 *
	 * @param roots roots of the object graphs to validate
	 * @return the structured validation result
	 * @throws NullPointerException if the collection or any root is {@code null}
	 * @throws IllegalArgumentException if the collection is empty
	 * @throws IllegalStateException if this validator has been closed
	 */
	OCLValidationResult validateAll(Collection<? extends EObject> roots);

	/**
	 * Validates one root and throws when it is not OCL-valid with respect to the configured
	 * constraints.
	 *
	 * @param root root of the object graph to validate
	 * @throws OCLValidationException if validation fails or evaluation/setup fails
	 */
	default void validateOrThrow(EObject root) {
		Objects.requireNonNull(root, "root must not be null");
		OCLValidationResult result = Objects.requireNonNull(validate(root),
				"validate(root) must not return null");
		if (!result.isValid()) {
			throw new OCLValidationException(result);
		}
	}

	/**
	 * Validates several roots and throws when they are not OCL-valid with respect to the configured
	 * constraints.
	 *
	 * @param roots roots of the object graphs to validate
	 * @throws OCLValidationException if validation fails or evaluation/setup fails
	 */
	default void validateAllOrThrow(Collection<? extends EObject> roots) {
		Objects.requireNonNull(roots, "roots must not be null");
		OCLValidationResult result = Objects.requireNonNull(validateAll(roots),
				"validateAll(roots) must not return null");
		if (!result.isValid()) {
			throw new OCLValidationException(result);
		}
	}

	/** Releases constraint-source resources owned by this validation session. */
	@Override
	void close();
}
