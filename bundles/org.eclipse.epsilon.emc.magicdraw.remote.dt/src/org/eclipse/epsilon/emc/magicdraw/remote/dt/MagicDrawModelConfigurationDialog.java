/*******************************************************************************
 * Copyright (c) 2022 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Antonio Garcia-Dominguez - initial API and implementation
 *******************************************************************************/
package org.eclipse.epsilon.emc.magicdraw.remote.dt;

import org.eclipse.epsilon.common.dt.launching.dialogs.AbstractCachedModelConfigurationDialog;
import org.eclipse.swt.widgets.Composite;

public class MagicDrawModelConfigurationDialog extends AbstractCachedModelConfigurationDialog {

	@Override
	protected String getModelName() {
		return "Magic Draw Remote Instance";
	}

	@Override
	protected String getModelType() {
		return "MagicDrawRemote";
	}

	@Override
	protected void createGroups(Composite control) {
		super.createGroups(control);
		createLoadStoreOptionsGroup(control);
	}

}
