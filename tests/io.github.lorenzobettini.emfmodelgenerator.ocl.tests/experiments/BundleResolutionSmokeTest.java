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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

class BundleResolutionSmokeTest {

	@Test
	void productionBundleAndCompatibleDependenciesResolveHeadlessly() throws ClassNotFoundException {
		Bundle testBundle = FrameworkUtil.getBundle(getClass());
		assertNotNull(testBundle);

		Bundle productionBundle = findBundle(testBundle,
				"io.github.lorenzobettini.emfmodelgenerator.ocl");
		assertNotNull(productionBundle);
		Bundle coreBundle = findBundle(testBundle, "emf-model-generator");
		assertNotNull(coreBundle);
		Version coreVersion = coreBundle.getVersion();
		assertTrue(coreVersion.compareTo(Version.parseVersion("1.1.0")) >= 0);
		assertTrue(coreVersion.compareTo(Version.parseVersion("2.0.0")) < 0);
		assertNotNull(productionBundle.loadClass(
				"io.github.lorenzobettini.emfmodelgenerator.EMFModelGenerator"));
		assertNotNull(productionBundle.loadClass(
				"org.eclipse.ocl.pivot.utilities.PivotStandaloneSetup"));
		assertNotNull(productionBundle.loadClass(
				"org.eclipse.ocl.xtext.essentialocl.EssentialOCLStandaloneSetup"));
		assertNotNull(productionBundle.loadClass(
				"org.eclipse.ocl.xtext.completeocl.CompleteOCLStandaloneSetup"));
		assertNotNull(productionBundle.loadClass(
				"org.eclipse.ocl.xtext.oclinecore.OCLinEcoreStandaloneSetup"));
	}

	private static Bundle findBundle(Bundle bundle, String symbolicName) {
		for (Bundle candidate : bundle.getBundleContext().getBundles()) {
			if (symbolicName.equals(candidate.getSymbolicName())) {
				return candidate;
			}
		}
		return null;
	}
}
