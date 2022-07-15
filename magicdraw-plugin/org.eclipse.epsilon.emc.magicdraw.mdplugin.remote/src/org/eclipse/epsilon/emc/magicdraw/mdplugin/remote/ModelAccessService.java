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
package org.eclipse.epsilon.emc.magicdraw.mdplugin.remote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.BooleanCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.DoubleCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetFeatureValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HasTypeRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HasTypeResponse;
import org.eclipse.epsilon.emc.magicdraw.modelapi.IntegerCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.StringCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.foundation.MDObject;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.magicdraw.uml.Finder;

import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

public class ModelAccessService extends ModelServiceGrpc.ModelServiceImplBase {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessService.class);
	
	@Override
	public void allOf(AllOfRequest request, StreamObserver<ModelElementCollection> responseObserver) {
		Project project = Application.getInstance().getProject();
		if (project == null) {
			responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.FAILED_PRECONDITION)));
			LOGGER.warn("Project is not open yet");
			return;
		}

		EClassifier eClassifier;
		if (request.getTypeName() != null) {
			eClassifier = findEClassifier(request.getMetamodelUri(), request.getTypeName());
			if (eClassifier == null) {
				responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.INVALID_ARGUMENT)));
				return;
			}
		} else {
			eClassifier = null;
		}

		EObject root;
		final String rootElementHyperlink = request.getRootElementHyperlink();
		if (rootElementHyperlink == null || rootElementHyperlink.length() == 0) {
			root = project.getPrimaryModel(); 
		} else {
			BaseElement element = Finder.byHyperlink().find(project, rootElementHyperlink);
			if (element == null) {
				LOGGER.warn(String.format("Could not find element with URI %s", rootElementHyperlink));
				responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.INVALID_ARGUMENT)));
				return;
			} else if (!(element instanceof EObject)) {
				LOGGER.warn(String.format("Element with URI %s is not an EObject", rootElementHyperlink));
				responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.INVALID_ARGUMENT)));
				return;
			} else {
				root = (EObject) element;
			}
		}

		final ModelElementCollection allOfResponse = getAllOf(eClassifier, root, request.getOnlyExactType());
		responseObserver.onNext(allOfResponse);
		responseObserver.onCompleted();
	}

	private ModelElementCollection getAllOf(EClassifier eClassifier, EObject root, final boolean onlyExactType) {
		final ModelElementCollection.Builder builder = ModelElementCollection.newBuilder();
		final TreeIterator<EObject> it = EcoreUtil.getAllProperContents(root, true);

		Predicate<EObject> pred;
		if (eClassifier == null) {
			pred = (eob) -> eob instanceof MDObject;
		}
		else if (onlyExactType) {
			pred = (eob) -> eob instanceof MDObject && eob.eClass() == eClassifier;
		}
		else {
			pred = (eob) -> eob instanceof MDObject && eClassifier.isInstance(eob);
		}

		while (it.hasNext()) {
			EObject eob = it.next();
			if (pred.test(eob)) {
				builder.addValues(encodeModelElement((MDObject) eob));
			}
		}

		return builder.build();
	}

	private ModelElement encodeModelElement(MDObject eob) {
		return ModelElement.newBuilder()
				.setElementID(eob.getID())
				.setMetamodelUri(eob.eClass().getEPackage().getNsURI())
				.setTypeName(eob.eClass().getName())
				.build();
	}

	private EClassifier findEClassifier(String metamodelUri, String typeName) {
		if (metamodelUri != null && !metamodelUri.isEmpty()) {
			EPackage pkg = EPackage.Registry.INSTANCE.getEPackage(metamodelUri);
			EClassifier eClassifier = pkg.getEClassifier(typeName);
			if (eClassifier == null) {
				LOGGER.warn(String.format("Cannot find type '%s::%s'", metamodelUri, typeName));
			}
			return eClassifier;
		} else {
			List<EClassifier> options = new ArrayList<>();
			for (Object oPkg : EPackage.Registry.INSTANCE.values()) {
				EClassifier option = ((EPackage) oPkg).getEClassifier(typeName);
				if (option != null) {
					options.add(option);
				}
			}

			if (options.size() == 0) {
				LOGGER.warn(String.format("Cannot find type '%s'", typeName));
				return null;
			} else if (options.size() > 1) {
				List<String> nsURIs = options.stream().map(e -> e.getEPackage().getNsURI()).collect(Collectors.toList());
				LOGGER.warn(String.format("Type '%s' is ambiguous (available in multiple nsURIs: %s)", typeName, nsURIs));
			}
			return options.get(0);
		}
	}

	@Override
	public void getFeatureValue(GetFeatureValueRequest request, StreamObserver<Value> responseObserver) {
		Project project = Application.getInstance().getProject();
		if (project == null) {
			responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.FAILED_PRECONDITION)));
			LOGGER.warn("Project is not open yet");
			return;
		}

		final MDObject mdObject = (MDObject) project.getElementByID(request.getElementID());
		if (mdObject == null) {
			responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.INVALID_ARGUMENT)));
			LOGGER.warn(String.format("No object exists with ID '%s'", request.getElementID()));
			return;
		}

		final Value.Builder vBuilder = Value.newBuilder();
		final EStructuralFeature eFeature = mdObject.eClass().getEStructuralFeature(request.getFeatureName());
		if (eFeature == null) {
			vBuilder.setNotDefined(true);
			LOGGER.warn(String.format("Feature '%s' is not defined for element type '%s::%s'", request.getFeatureName(), mdObject.eClass().getEPackage().getNsURI(), mdObject.eClass().getName()));
		} else {
			final Object rawValue = mdObject.eGet(eFeature);
			if (rawValue != null) {
				if (eFeature instanceof EReference) {
					encodeReference((EReference) eFeature, rawValue, vBuilder);
				} else if (eFeature instanceof EAttribute) {
					encodeAttribute(eFeature, vBuilder, rawValue);
				} else {
					LOGGER.warn(String.format("Unknown feature type '%s'", eFeature.eClass().getName()));
					responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.INVALID_ARGUMENT)));
				}
			}
		}

		responseObserver.onNext(vBuilder.build());
		responseObserver.onCompleted();
	}

	private void encodeAttribute(final EStructuralFeature eFeature, Value.Builder vBuilder, final Object rawValue) {
		if (eFeature.isMany()) {
			encodeManyScalars(vBuilder, rawValue);
		} else {
			encodeScalar(vBuilder, rawValue);
		}
	}

	private void encodeScalar(Value.Builder vBuilder, final Object rawValue) {
		if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer || rawValue instanceof Long) {
			vBuilder.setLongValue((byte) rawValue);
		} else if (rawValue instanceof Float || rawValue instanceof Double) {
			vBuilder.setDoubleValue((float) rawValue);
		} else if (rawValue instanceof String) {
			vBuilder.setStringValue((String) rawValue);
		} else if (rawValue instanceof Boolean) {
			vBuilder.setBooleanValue((boolean) rawValue);
		}
	}

	private void encodeManyScalars(Value.Builder vBuilder, final Object rawValue) {
		final Iterator<?> it = ((Collection<?>) rawValue).iterator();
		final Object firstValue = it.next();
		if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer || rawValue instanceof Long) {
			IntegerCollection.Builder iBuilder = IntegerCollection.newBuilder();
			iBuilder.addValues((long) firstValue);
			while (it.hasNext()) iBuilder.addValues((long) it.next());
			vBuilder.setLongValues(iBuilder.build());
		} else if (rawValue instanceof Float || rawValue instanceof Double) {
			DoubleCollection.Builder iBuilder = DoubleCollection.newBuilder();
			iBuilder.addValues((double) firstValue);
			while (it.hasNext()) iBuilder.addValues((double) it.next());
			vBuilder.setDoubleValues(iBuilder.build());
		} else if (rawValue instanceof String) {
			StringCollection.Builder iBuilder = StringCollection.newBuilder();
			iBuilder.addValues((String) firstValue);
			while (it.hasNext()) iBuilder.addValues((String) it.next());
			vBuilder.setStringValues(iBuilder.build());
		} else if (rawValue instanceof Boolean) {
			BooleanCollection.Builder iBuilder = BooleanCollection.newBuilder();
			iBuilder.addValues((boolean) firstValue);
			while (it.hasNext()) iBuilder.addValues((boolean) it.next());
			vBuilder.setBooleanValues(iBuilder.build());
		}
	}

	@SuppressWarnings("unchecked")
	private void encodeReference(EReference eReference, final Object rawValue, Value.Builder vBuilder) {
		if (eReference.isMany()) {
			ModelElementCollection.Builder cBuilder = ModelElementCollection.newBuilder();
			for (Iterator<MDObject> it = ((Iterable<MDObject>) rawValue).iterator(); it.hasNext(); ) {
				cBuilder.addValues(encodeModelElement(it.next()));
			}
			vBuilder.setReferenceValues(cBuilder.build());
		} else {
			vBuilder.setReferenceValue(encodeModelElement((MDObject) rawValue));
		}
	}

	@Override
	public void hasType(HasTypeRequest request, StreamObserver<HasTypeResponse> responseObserver) {
		EClassifier eClassifier = findEClassifier(request.getMetamodelUri(), request.getTypeName());
		responseObserver.onNext(HasTypeResponse.newBuilder().setHasType(eClassifier != null).build());
		responseObserver.onCompleted();
	}

}