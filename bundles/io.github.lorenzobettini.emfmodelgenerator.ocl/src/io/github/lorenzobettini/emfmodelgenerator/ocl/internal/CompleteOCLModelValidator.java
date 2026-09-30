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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.util.BasicDiagnostic;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.ocl.pivot.internal.validation.PivotEObjectValidator;
import org.eclipse.ocl.pivot.utilities.OCL;
import org.eclipse.ocl.pivot.utilities.PivotStandaloneSetup;
import org.eclipse.ocl.pivot.validation.ComposedEValidator;
import org.eclipse.ocl.pivot.validation.ValidationRegistryAdapter;
import org.eclipse.ocl.xtext.completeocl.CompleteOCLStandaloneSetup;
import org.eclipse.ocl.xtext.completeocl.utilities.CompleteOCLLoader;
import org.eclipse.ocl.xtext.essentialocl.EssentialOCLStandaloneSetup;

import io.github.lorenzobettini.emfmodelgenerator.EMFValidationResult;
import io.github.lorenzobettini.emfmodelgenerator.ocl.OCLModelValidator;

/** Internal adapter containing all Eclipse Complete OCL implementation dependencies. */
public final class CompleteOCLModelValidator implements OCLModelValidator {

	private static final String COMPLETE_OCL_EXTENSION = "ocl";

	static {
		PivotStandaloneSetup.init();
		EssentialOCLStandaloneSetup.doSetup();
		CompleteOCLStandaloneSetup.doSetup();
	}

	private final ResourceSet resourceSet;
	private final ResourceFactoryRegistration resourceFactoryRegistration;
	private final Set<Adapter> existingAdapters;
	private final Set<Resource> ownedResources;
	private final OCL ocl;
	private final SessionCompleteOCLLoader loader;
	private final OCLValidationRunner validationRunner;
	private final EMFValidationResult setupFailure;
	private boolean closed;

	/**
	 * Creates a session that parses {@code oclURI} once and composes its validators locally.
	 *
	 * @param resourceSet caller-owned resource set
	 * @param oclURI external Complete OCL document
	 */
	public CompleteOCLModelValidator(ResourceSet resourceSet, URI oclURI) {
		this.resourceSet = Objects.requireNonNull(resourceSet, "resourceSet must not be null");
		Objects.requireNonNull(oclURI, "oclURI must not be null");
		resourceFactoryRegistration = ResourceFactoryRegistration.install(
				resourceSet, COMPLETE_OCL_EXTENSION);
		existingAdapters = identitySet(resourceSet.eAdapters());
		Set<Resource> existingResources = identitySet(resourceSet.getResources());
		ocl = OCL.newInstance(resourceSet);
		loader = new SessionCompleteOCLLoader(ocl);
		setupFailure = loadAndInstall(oclURI);
		ownedResources = identitySet(resourceSet.getResources());
		ownedResources.removeAll(existingResources);
		ValidationRegistryAdapter registry = ValidationRegistryAdapter.getAdapter(resourceSet);
		var diagnostician = registry.getDiagnostician();
		var validationContext = diagnostician.createDefaultContext();
		validationRunner = new OCLValidationRunner(ocl, diagnostician, validationContext,
				"Complete OCL validation", CompleteOCLModelValidator::failure);
	}

	@Override
	public synchronized EMFValidationResult validate(EObject root) {
		Objects.requireNonNull(root, "root must not be null");
		checkOpen();
		if (setupFailure != null) {
			return setupFailure;
		}
		return validationRunner.validate(root);
	}

	@Override
	public synchronized EMFValidationResult validateAll(Collection<? extends EObject> roots) {
		Objects.requireNonNull(roots, "roots must not be null");
		checkOpen();
		validateRootsArgument(roots);
		if (setupFailure != null) {
			return setupFailure;
		}
		return validationRunner.validateAll(roots);
	}

	@Override
	public synchronized void close() {
		if (closed) {
			return;
		}
		closed = true;
		loader.restoreRegistrations();
		loader.dispose();
		ocl.dispose();
		resourceFactoryRegistration.restore();
		for (Resource resource : ownedResources) {
			resource.unload();
			resourceSet.getResources().remove(resource);
		}
		resourceSet.eAdapters().removeIf(adapter -> !existingAdapters.contains(adapter)
				&& adapter.getClass().getName().startsWith("org.eclipse.ocl."));
	}

	private EMFValidationResult loadAndInstall(URI oclURI) {
		ocl.activate();
		try {
			var errors = new StringBuilder();
			if (!loader.loadDocument(oclURI, errors)) {
				return failure("Unable to load Complete OCL document " + oclURI + ":\n" + errors);
			}
			if (!loader.installLocalValidators()) {
				return failure("Complete OCL document " + oclURI
						+ " does not complement an imported EPackage");
			}
			return null;
		} catch (RuntimeException exception) {
			return failure("Unable to load Complete OCL document " + oclURI, exception);
		} finally {
			ocl.deactivate();
		}
	}

	private static EMFValidationResult failure(RuntimeException exception) {
		return failure("Complete OCL evaluation/setup failure", exception);
	}

	private static EMFValidationResult failure(String message) {
		return failure(message, null);
	}

	private static EMFValidationResult failure(String message, RuntimeException exception) {
		String detail = exception == null ? null : exception.getMessage();
		String fullMessage = detail == null || detail.isBlank()
				? message
				: message + ": " + detail;
		Object[] data = exception == null ? null : new Object[] { exception };
		var diagnostic = new BasicDiagnostic(Diagnostic.ERROR,
				OCLValidationRunner.DIAGNOSTIC_SOURCE, 0,
				fullMessage, data);
		return OCLValidationRunner.validatorFailure(diagnostic);
	}

	private static void validateRootsArgument(Collection<? extends EObject> roots) {
		for (EObject root : roots) {
			Objects.requireNonNull(root, "roots must not contain null");
		}
	}

	private void checkOpen() {
		if (closed) {
			throw new IllegalStateException("validator has been closed");
		}
	}

	private static <T> Set<T> identitySet(Collection<? extends T> values) {
		Set<T> result = Collections.newSetFromMap(new IdentityHashMap<>());
		result.addAll(values);
		return result;
	}

	private static final class SessionCompleteOCLLoader extends CompleteOCLLoader {

		private final List<Registration> registrations = new ArrayList<>();

		SessionCompleteOCLLoader(OCL ocl) {
			super(ocl.getEnvironmentFactory());
		}

		@Override
		protected boolean error(String primaryMessage, String detailMessage) {
			return false;
		}

		boolean installLocalValidators() {
			if (mmPackages.isEmpty()) {
				return false;
			}
			ValidationRegistryAdapter registry = ValidationRegistryAdapter.getAdapter(
					ocl.getResourceSet());
			PivotEObjectValidator validator = new PivotEObjectValidator(List.copyOf(oclModels));
			for (EPackage ePackage : mmPackages) {
				boolean hadLocalRegistration = registry.containsKey(ePackage);
				Object previous = hadLocalRegistration ? registry.get(ePackage) : null;
				ComposedEValidator.install(registry, ePackage, validator);
				registrations.add(new Registration(registry, ePackage,
						hadLocalRegistration, previous));
			}
			return true;
		}

		void restoreRegistrations() {
			for (int i = registrations.size() - 1; i >= 0; i--) {
				Registration registration = registrations.get(i);
				if (registration.hadLocalRegistration()) {
					registration.registry().put(registration.ePackage(), registration.previous());
				} else {
					registration.registry().remove(registration.ePackage());
				}
			}
			registrations.clear();
		}
	}

	private record Registration(ValidationRegistryAdapter registry, EPackage ePackage,
			boolean hadLocalRegistration, Object previous) {
	}

}
