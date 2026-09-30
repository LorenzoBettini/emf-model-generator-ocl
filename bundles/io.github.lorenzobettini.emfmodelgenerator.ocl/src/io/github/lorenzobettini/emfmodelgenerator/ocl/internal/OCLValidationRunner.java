/*******************************************************************************
 * Copyright (c) 2026 Lorenzo Bettini and contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.github.lorenzobettini.emfmodelgenerator.ocl.internal;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.ocl.pivot.utilities.OCL;

import io.github.lorenzobettini.emfmodelgenerator.EMFValidationKind;
import io.github.lorenzobettini.emfmodelgenerator.EMFValidationResult;

/** Executes the validation mechanics shared by the supported OCL constraint sources. */
final class OCLValidationRunner {

	static final String DIAGNOSTIC_SOURCE =
			"io.github.lorenzobettini.emfmodelgenerator.ocl";
	private static final int REJECTED_SEVERITY = Diagnostic.WARNING;

	private final OCL ocl;
	private final Diagnostician diagnostician;
	private final Map<Object, Object> validationContext;
	private final String aggregateDescription;
	private final Function<RuntimeException, EMFValidationResult> failureFactory;

	OCLValidationRunner(OCL ocl, Diagnostician diagnostician,
			Map<Object, Object> validationContext, String aggregateDescription,
			Function<RuntimeException, EMFValidationResult> failureFactory) {
		this.ocl = Objects.requireNonNull(ocl);
		this.diagnostician = Objects.requireNonNull(diagnostician);
		this.validationContext = Objects.requireNonNull(validationContext);
		this.aggregateDescription = Objects.requireNonNull(aggregateDescription);
		this.failureFactory = Objects.requireNonNull(failureFactory);
	}

	EMFValidationResult validate(EObject root) {
		Objects.requireNonNull(root, "root must not be null");
		try {
			Diagnostic diagnostic = validateDiagnostic(root);
			return result(diagnostic, containsThrowable(diagnostic));
		} catch (RuntimeException exception) {
			return failureFactory.apply(exception);
		}
	}

	EMFValidationResult validateAll(Collection<? extends EObject> roots) {
		Objects.requireNonNull(roots, "roots must not be null");
		var aggregate = new BasicDiagnostic(DIAGNOSTIC_SOURCE, 0,
				aggregateDescription + " of " + roots.size() + " roots", null);
		boolean setupOrEvaluationFailed = false;
		for (EObject root : roots) {
			Objects.requireNonNull(root, "roots must not contain null");
			try {
				Diagnostic diagnostic = validateDiagnostic(root);
				aggregate.add(diagnostic);
				setupOrEvaluationFailed |= containsThrowable(diagnostic);
			} catch (RuntimeException exception) {
				aggregate.add(failureFactory.apply(exception).diagnostic());
				setupOrEvaluationFailed = true;
			}
		}
		return result(aggregate, setupOrEvaluationFailed);
	}

	private Diagnostic validateDiagnostic(EObject root) {
		ocl.activate();
		try {
			return diagnostician.validate(root, validationContext);
		} finally {
			ocl.deactivate();
		}
	}

	static EMFValidationResult validatorFailure(Diagnostic diagnostic) {
		return result(diagnostic, true);
	}

	private static EMFValidationResult result(Diagnostic diagnostic, boolean validatorFailure) {
		var kind = diagnostic.getSeverity() < REJECTED_SEVERITY
				? EMFValidationKind.VALID
				: validatorFailure
						? EMFValidationKind.VALIDATOR_FAILURE
						: EMFValidationKind.VALIDATION_FAILURE;
		return new EMFValidationResult(diagnostic, REJECTED_SEVERITY, kind);
	}

	private static boolean containsThrowable(Diagnostic diagnostic) {
		if (diagnostic.getData().stream().anyMatch(Throwable.class::isInstance)) {
			return true;
		}
		return diagnostic.getChildren().stream()
				.anyMatch(OCLValidationRunner::containsThrowable);
	}
}
