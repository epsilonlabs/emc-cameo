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

import java.io.File;

import org.eclipse.epsilon.common.dt.launching.dialogs.AbstractCachedModelConfigurationDialog;
import org.eclipse.epsilon.common.dt.util.DialogUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceConstants;
import org.eclipse.epsilon.emc.magicdraw.remote.MagicDrawModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class MagicDrawModelConfigurationDialog extends AbstractCachedModelConfigurationDialog {

	private static final String ERROR_PORT_FORMAT = "The port must be an integer number greater than 0.";

	private Text hostText;
	private Text portText;
	private Text rootHyperlinkText;

	private Button closeOnDisposalCheck;
	private Text projectURLText;

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
		createMDOptionsGroup(control);
		createLoadStoreOptionsGroup(control);
	}

	private void createMDOptionsGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "MagicDraw/Cameo Options", 3);

		Label projectURLLabel = new Label(groupContent, SWT.NONE);
		projectURLLabel.setText("Project URL:");

		projectURLText = new Text(groupContent, SWT.BORDER);
		projectURLText.setLayoutData(fillHorizontal());

		Button projectURLBrowse = new Button(groupContent, SWT.NONE);
		projectURLBrowse.setText("Browse...");
		projectURLBrowse.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog fileDialog = new FileDialog(parent.getShell());
				fileDialog.setText("Select file");
				fileDialog.setFilterExtensions(new String[] { "*.mdzip" });
				fileDialog.setFilterNames(new String[] { "MagicDraw/Cameo projects (*.mdzip)" });

				String selected = fileDialog.open();
				if (selected != null) {
					projectURLText.setText(new File(selected).toURI().toString());
				}
			}
		});

		Label rootHyperlinkLabel = new Label(groupContent, SWT.NONE);
		rootHyperlinkLabel.setText("Root element hyperlink:");
		rootHyperlinkLabel.setToolTipText("Hyperlink to the package that should act as the root of the model "
				+ "(for X.all and new instances): this can be found out by right-clicking "
				+ "on the element and selecting 'Copy Element Hyperlink'");

		rootHyperlinkText = new Text(groupContent, SWT.BORDER);
		rootHyperlinkText.setLayoutData(fillHorizontal(2));

		Label closeOnDisposalLabel = new Label(groupContent, SWT.NONE);
		closeOnDisposalLabel.setText("Close on disposal:");
		closeOnDisposalCheck = new Button(groupContent, SWT.CHECK);		
		closeOnDisposalCheck.setLayoutData(fillHorizontal(2));		

		groupContent.layout();
		groupContent.pack();
	}

	private GridData fillHorizontal() {
		return fillHorizontal(null);
	}

	private GridData fillHorizontal(Integer columns) {
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		if (columns != null) {
			gd.horizontalSpan = columns;
		}
		return gd;
	}

	private void createConnectionOptionsGroup(Composite parent) {
		final Composite groupContent = DialogUtil.createGroupContainer(parent, "Connection Options", 2);

		Label hostLabel = new Label(groupContent, SWT.NONE);
		hostLabel.setText("Host: ");

		hostText = new Text(groupContent, SWT.BORDER);
		hostText.setLayoutData(fillHorizontal());

		Label portLabel = new Label(groupContent, SWT.NONE);
		portLabel.setText("Port: ");

		portText = new Text(groupContent, SWT.BORDER);
		portText.setLayoutData(fillHorizontal());
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
		rootHyperlinkText.setText(properties.getProperty(MagicDrawModel.PROPERTY_ROOT_HYPERLINK));
		closeOnDisposalCheck.setSelection(properties.getBooleanProperty(MagicDrawModel.PROPERTY_CLOSE_ON_DISPOSAL, false));
		projectURLText.setText(properties.getProperty(MagicDrawModel.PROPERTY_PROJECT_URL, ""));
	}

	@Override
	protected void storeProperties() {
		super.storeProperties();

		properties.put(MagicDrawModel.PROPERTY_HOST, hostText.getText());
		properties.put(MagicDrawModel.PROPERTY_PORT, portText.getText());
		properties.put(MagicDrawModel.PROPERTY_ROOT_HYPERLINK, rootHyperlinkText.getText());
		properties.put(MagicDrawModel.PROPERTY_CLOSE_ON_DISPOSAL, closeOnDisposalCheck.getSelection());
		properties.put(MagicDrawModel.PROPERTY_PROJECT_URL, projectURLText.getText());
	}

}
