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
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.ocl.pivot.internal.delegate.OCLDelegateDomain;
import org.eclipse.ocl.pivot.internal.delegate.OCLValidationDelegateFactory;
import org.eclipse.ocl.pivot.internal.delegate.ValidationDelegate;
import org.eclipse.ocl.pivot.utilities.OCL;
import org.eclipse.ocl.pivot.utilities.PivotConstants;
import org.eclipse.ocl.pivot.utilities.PivotStandaloneSetup;
import org.eclipse.ocl.pivot.validation.ValidationRegistryAdapter;
import org.eclipse.ocl.xtext.essentialocl.EssentialOCLStandaloneSetup;
import org.eclipse.ocl.xtext.oclinecore.OCLinEcoreStandaloneSetup;

import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLDiagnosticKind;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLValidationResult;

/** Internal adapter containing all Eclipse OCL delegate implementation dependencies. */
public final class OCLinEcoreModelValidator implements OCLModelValidator {

	private static final String ESSENTIAL_OCL_EXTENSION = "essentialocl";

	static {
		PivotStandaloneSetup.init();
		EssentialOCLStandaloneSetup.doSetup();
		OCLinEcoreStandaloneSetup.doSetup();
	}

	private final ResourceSet resourceSet;
	private final OCL ocl;
	private final OCLValidationRunner validationRunner;
	private final Set<Adapter> existingAdapters;
	private final ResourceFactoryRegistration resourceFactoryRegistration;
	private boolean closed;

	/**
	 * Creates a session whose model-specific registrations are local to {@code resourceSet}.
	 *
	 * @param resourceSet caller-owned resource set
	 */
	public OCLinEcoreModelValidator(ResourceSet resourceSet) {
		this.resourceSet = Objects.requireNonNull(resourceSet, "resourceSet must not be null");
		resourceFactoryRegistration = ResourceFactoryRegistration.install(
				resourceSet, ESSENTIAL_OCL_EXTENSION);
		existingAdapters = identitySet(resourceSet.eAdapters());
		OCLDelegateDomain.lazyInitializeLocals(resourceSet,
				PivotConstants.OCL_DELEGATE_URI_PIVOT, true, null);
		var delegates = new ValidationDelegate.Factory.Registry.Impl();
		delegates.put(PivotConstants.OCL_DELEGATE_URI_PIVOT,
				new OCLValidationDelegateFactory(PivotConstants.OCL_DELEGATE_URI_PIVOT));
		var diagnostician = ValidationRegistryAdapter.getAdapter(resourceSet).getDiagnostician();
		var validationContext = diagnostician.createDefaultContext();
		validationContext.put(EValidator.ValidationDelegate.Registry.class, delegates);
		ocl = OCL.newInstance(resourceSet);
		// Creation implicitly attaches the environment to this thread; keep the session dormant
		// between validation calls so other OCL environments can use the same thread.
		ocl.deactivate();
		validationRunner = new OCLValidationRunner(ocl, diagnostician, validationContext,
				"OCL validation", OCLinEcoreModelValidator::setupOrEvaluationFailure);
	}

	@Override
	public synchronized OCLValidationResult validate(EObject root) {
		Objects.requireNonNull(root, "root must not be null");
		checkOpen();
		return validationRunner.validate(root);
	}

	@Override
	public synchronized OCLValidationResult validateAll(Collection<? extends EObject> roots) {
		Objects.requireNonNull(roots, "roots must not be null");
		checkOpen();
		return validationRunner.validateAll(roots);
	}

	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		try {
			ocl.dispose();
			resourceSet.eAdapters().removeIf(adapter -> !existingAdapters.contains(adapter)
					&& adapter.getClass().getName().startsWith("org.eclipse.ocl."));
		} finally {
			resourceFactoryRegistration.restore();
		}
	}

	private static OCLValidationResult setupOrEvaluationFailure(RuntimeException exception) {
		String detail = exception.getMessage();
		String message = "OCL evaluation/setup failure: "
				+ (detail == null || detail.isBlank()
						? exception.getClass().getSimpleName()
						: detail);
		var diagnostic = new BasicDiagnostic(Diagnostic.ERROR,
				OCLValidationRunner.DIAGNOSTIC_SOURCE, 0,
				message, new Object[] { exception });
		return new OCLValidationResult(diagnostic,
				OCLValidationResult.DEFAULT_REJECTED_SEVERITY,
				OCLDiagnosticKind.EVALUATION_OR_SETUP_FAILURE);
	}

	private void checkOpen() {
		if (closed) {
			throw new IllegalStateException("validator has been closed");
		}
	}

	private static Set<Adapter> identitySet(Collection<? extends Adapter> adapters) {
		Set<Adapter> result = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		result.addAll(adapters);
		return result;
	}

}
