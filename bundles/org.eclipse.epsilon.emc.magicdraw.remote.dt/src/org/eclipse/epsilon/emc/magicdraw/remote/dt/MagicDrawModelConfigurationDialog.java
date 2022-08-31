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
import org.eclipse.epsilon.common.dt.util.DialogUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceConstants;
import org.eclipse.epsilon.emc.magicdraw.remote.MagicDrawModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class MagicDrawModelConfigurationDialog extends AbstractCachedModelConfigurationDialog {

	private static final String ERROR_PORT_FORMAT = "The port must be an integer number greater than 0.";

	private Label hostLabel;
	private Text hostText;
	private Label portLabel;
	private Text portText;

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
		createConnectionOptionsGroup(control);
		createLoadStoreOptionsGroup(control);
	}

	private void createConnectionOptionsGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "Connection Options", 2);

		hostLabel = new Label(groupContent, SWT.NONE);
		hostLabel.setText("Host: ");

		hostText = new Text(groupContent, SWT.BORDER);
		hostText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		portLabel = new Label(groupContent, SWT.NONE);
		portLabel.setText("Port: ");

		portText = new Text(groupContent, SWT.BORDER);
		portText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		portText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				setErrorMessage(null);

				String sPort = portText.getText();
				try {
					int port = Integer.parseInt(sPort);
					if (port < 1) {
						setErrorMessage(ERROR_PORT_FORMAT);
					}
				} catch (NumberFormatException ex) {
					setErrorMessage(ERROR_PORT_FORMAT);
				}
			}
		});

		groupContent.layout();
		groupContent.pack();
	}

	@Override
	protected void loadProperties() {
		super.loadProperties();
		if (properties == null) return;

		hostText.setText(properties.getProperty(MagicDrawModel.PROPERTY_HOST, ModelServiceConstants.DEFAULT_HOST));
		portText.setText(properties.getProperty(MagicDrawModel.PROPERTY_PORT, ModelServiceConstants.DEFAULT_PORT + ""));
	}

	@Override
	protected void storeProperties() {
		super.storeProperties();

		properties.put(MagicDrawModel.PROPERTY_HOST, hostText.getText());
		properties.put(MagicDrawModel.PROPERTY_PORT, portText.getText());
	}

}
