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

import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

/** A reversible resource-factory registration in a caller-owned resource set. */
record ResourceFactoryRegistration(
		Map<String, Object> localFactories,
		String extension,
		boolean hadPrevious,
		Object previous) {

	static ResourceFactoryRegistration install(ResourceSet resourceSet, String extension) {
		Map<String, Object> localFactories = resourceSet.getResourceFactoryRegistry()
				.getExtensionToFactoryMap();
		boolean hadPrevious = localFactories.containsKey(extension);
		Object previous = localFactories.get(extension);
		Object oclFactory = Objects.requireNonNull(
				Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().get(extension),
				"No global resource factory for extension " + extension);
		localFactories.put(extension, oclFactory);
		return new ResourceFactoryRegistration(localFactories, extension, hadPrevious, previous);
	}

	void restore() {
		if (hadPrevious) {
			localFactories.put(extension, previous);
		} else {
			localFactories.remove(extension);
		}
	}
}
