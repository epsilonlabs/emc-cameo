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

import static org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ModelUtils.getFullyQualifiedName;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ModelUtils;
import org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ValueDecoder;
import org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ValueEncoder;
import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.CreateInstanceRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.DeleteInstanceRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Empty;
import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValue;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetElementByIDRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetFeatureValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetTypeRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ListPosition;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ListPositionValue;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementType;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementTypeReference;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceConstants;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.OpenSessionRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ProjectLocation;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ProxyList;
import org.eclipse.epsilon.emc.magicdraw.modelapi.SetFeatureValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.SingleInteger;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.rpc.ErrorInfo;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.core.project.ProjectDescriptor;
import com.nomagic.magicdraw.core.project.ProjectDescriptorsFactory;
import com.nomagic.magicdraw.core.project.ProjectsManager;
import com.nomagic.magicdraw.foundation.MDObject;
import com.nomagic.magicdraw.openapi.uml.ModelElementsManager;
import com.nomagic.magicdraw.openapi.uml.ReadOnlyElementException;
import com.nomagic.magicdraw.openapi.uml.SessionManager;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.magicdraw.uml.Finder;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.PackageableElement;
import com.nomagic.uml2.impl.ElementsFactory;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.StreamObserver;

/**
 * <p>Exposes the model opened in a running MagicDraw instance over gRPC.</p>
 */
public class ModelAccessService extends ModelServiceGrpc.ModelServiceImplBase {
	/*
	 * This class makes heavy use of an Either type (inspired by Scala's Either type)
	 * which represents a value of one of two types (our version allows for null values).
	 *
	 * We use it to DRY the code involved in error reporting: in gRPC, rather than
	 * using exceptions, we have to create StatusRuntimException objects an send
	 * them through the StreamObserver with an onError(...) call. This means that we
	 * cannot simply have methods that check something, and if the condition is not
	 * met, throw an exception which interrupts the rest of the processing of the
	 * API call.
	 *
	 * Instead, we consider the processing of a call as a computation that produces
	 * an Either<StatusRuntimeException, R>, where R is the type of response of the
	 * StreamObserver<R> for the call. Our version of Either has a flatMapRight(...)
	 * method that can take a function which consumes that "right" value, i.e.
	 * continues the processing if no error needs to be reported yet, and can
	 * produce a value of the same left type (a StatusRuntimeException), or of
	 * another type (which does not have to be the same as the previous right type).
	 *
	 * A sendResponse(obs, response) method takes that Either object and uses
	 * onError if it is of the left right, and onNext + onCompleted if it is of the
	 * right type.
	 *
	 * We can use the flatMapRight(...) method repeatedly to chain processing steps,
	 * and we can define private methods with reusable processing steps (e.g.
	 * checking if MagicDraw is inside of a project or not). This is better than
	 * copying and pasting invocations of onError + return statements when having to
	 * interrupt the processing because of a certain type of problem from multiple
	 * places.
	 */

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessService.class);
	private static final String GRPC_DOMAIN = ModelAccessService.class.getPackage().getName();

	private final ValueEncoder encoder = new ValueEncoder();
	private final ValueDecoder decoder = new ValueDecoder();

	@Override
	public void allOf(AllOfRequest request, StreamObserver<ModelElementCollection> responseObserver) {
		sendResponse(responseObserver, inProject().flatMapRight((project) ->
			findEClassifier(request.getTypeName()).flatMapRight((eClassifier) ->
				findRootElement(request.getRootElementHyperlink(), project).flatMapRight((root) ->
					Either.right(encoder.encodeAllOf(eClassifier, root, request.getOnlyExactType()))))));
	}

	private Either<StatusRuntimeException, EObject> findRootElement(String rootElementHyperlink, Project project) {
		if (rootElementHyperlink == null || rootElementHyperlink.trim().length() == 0) {
			return Either.right(project.getPrimaryModel());
		} else {
			BaseElement element = Finder.byHyperlink().find(project, rootElementHyperlink);
			if (element == null) {
				return Either.left(Status.INVALID_ARGUMENT
					.withDescription(String.format("Could not find element with URI %s", rootElementHyperlink))
					.asRuntimeException());
			} else if (!(element instanceof EObject)) {
				return Either.left(Status.INVALID_ARGUMENT
					.withDescription(String.format("Element with URI %s is not an EObject", rootElementHyperlink))
					.asRuntimeException());
			} else {
				return Either.right((EObject) element);
			}
		}
	}

	@Override
	public void getFeatureValue(GetFeatureValueRequest request, StreamObserver<Value> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> getObjectByID(project, request.getElementID()))
			.flatMapRight((mdObject) -> {
				final Value.Builder vBuilder = Value.newBuilder();
				final EStructuralFeature eFeature = mdObject.eClass()
					.getEStructuralFeature(request.getFeatureName());

				if (eFeature == null) {
					// Might be one of the special cases
					switch (request.getFeatureName()) {
						case "eContainer": {
							EObject eContainer = mdObject.eContainer();
							encoder.encodeReference(vBuilder, eContainer);
							break;
						}
						case "eContainingFeature": {
							EStructuralFeature feature = mdObject.eContainingFeature();
							if (feature != null) {
								encoder.encodeReference(vBuilder, feature);
							}
							break;
						}
						case "eContents": {
							ModelElementCollection.Builder coll = ModelElementCollection.newBuilder();
							for (EObject child : mdObject.eContents()) {
								coll.addValues(encoder.encode(child));
							}
							vBuilder.setReferenceValues(coll);
							break;
						}
						default:
							vBuilder.setNotDefined(true);
							break;
					}
				} else if (eFeature.isMany()) {
					vBuilder.setProxyList(ProxyList.newBuilder()
							.setElementID(encoder.encodeID(mdObject))
							.setFeatureName(eFeature.getName()));
				} else {
					Object rawValue = mdObject.eGet(eFeature);
					if (rawValue == null && eFeature instanceof EAttribute) {
						/*
						 * MagicDraw does not use default values consistently in their feature
						 * declarations: for instance, a class with a public visibility will have
						 * eGet(eFeature) return null, but getVisibility() will return the public
						 * enumerator value. Calling the get*() method directly via reflection
						 * always works, though, but it is much slower.
						 *
						 * Normally we would check if the eType of the feature is an EEnum and
						 * use the first literal as the default value [1], but it appears that
						 * NamedElementVisibilityKind is a custom EDataTypeImpl class with no
						 * clear link to an EEnum.
						 *
						 * We only do this for EAttributes as it only seems to be an issue
						 * right now for those enumeration-based properties.
						 *
						 * [1]: https://www.eclipse.org/forums/index.php?t=msg&th=168434/
						 */
						try {
							final String methodName = "get" + firstUppercase(eFeature.getName());
							final Method mGetMethod = mdObject.getClass().getMethod(methodName);
							rawValue = mGetMethod.invoke(mdObject);
						} catch (Exception e) {
							return Either.left(Status.INVALID_ARGUMENT.withDescription(String.format(
								"Failed to use reflection to get value of feature %s from an object of type %s",
								eFeature.getName(), getFullyQualifiedName(mdObject.eClass()))).asRuntimeException());
						}
					}

					if (rawValue != null) {
						encoder.encode(mdObject, eFeature, vBuilder, rawValue);
					}
				}
				return Either.right(vBuilder.build());
			}));
	}

	private String firstUppercase(String featureName) {
		return Character.toUpperCase(featureName.charAt(0)) + featureName.substring(1);
	}

	@Override
	public void getType(GetTypeRequest request, StreamObserver<ModelElementType> responseObserver) {
		sendResponse(responseObserver, findEClassifier(request.getTypeName())
			.flatMapRight((eClassifier) -> {
				final ModelElementType.Builder builder = ModelElementType.newBuilder()
					.setMetamodelUri(eClassifier.getEPackage().getNsPrefix())
					.setTypeName(getFullyQualifiedName(eClassifier))
					.setIsAbstract(eClassifier instanceof EClass && ((EClass) eClassifier).isAbstract());

				if (eClassifier instanceof EClass) {
					for (EClass supertype : ((EClass) eClassifier).getEAllSuperTypes()) {
						builder.addAllSupertypes(ModelElementTypeReference.newBuilder()
							.setMetamodelUri(supertype.getEPackage().getNsURI())
							.setTypeName(getFullyQualifiedName(supertype)).build());
					}
				}

				return Either.right(builder.build());
		}));
	}

	@Override
	public void getEnumerationValue(GetEnumerationValueRequest request, StreamObserver<EnumerationValue> responseObserver) {
		sendResponse(responseObserver, findEEnums(request.getEnumeration())
			.flatMapRight((eEnumOptions) -> {
				// Find the matching literal
				EEnumLiteral literal = null;
				for (EClassifier eClassifier : eEnumOptions) {
					if (eClassifier instanceof EEnum) {
						EEnum eEnum = (EEnum) eClassifier;
						literal = eEnum.getEEnumLiteral(request.getLabel());
						if (literal != null) break;
					}
				}

				// Report result
				if (literal == null) {
					return Either.left(Status.INVALID_ARGUMENT
						.withDescription(String.format("Could not find enumeration value '%s' in '%s'", request.getLabel(), request.getEnumeration()))
						.asRuntimeException());
				} else {
					return Either.right(encoder.encode((Enumerator) literal));
				}
			}));
	}

	@Override
	public void getElementByID(GetElementByIDRequest request, StreamObserver<ModelElement> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> getObjectByID(project, request.getElementID())
			.flatMapRight((mdObject) -> Either.right(encoder.encode(mdObject)))
		));
	}

	private Either<StatusRuntimeException, EObject> getObjectByID(Project project, final String id) {
		if (decoder.isResourceBasedID(id)) {
			EObject eob = decoder.findByResourceBasedID(project, id);
			if (eob == null) {
				return Either.left(Status.INVALID_ARGUMENT
					.withDescription(String.format("Could not find metamodel element with ID %s", id))
					.asRuntimeException());
			} else {
				return Either.right(eob);
			}
		}
		return getMDObjectByID(project, id);
	}

	private Either<StatusRuntimeException, EObject> getMDObjectByID(Project project, final String id) {
		final BaseElement element = project.getElementByID(id);
		if (element == null) {
			return Either.left(Status.INVALID_ARGUMENT
				.withDescription(String.format("Could not find element with ID %s", id))
				.asRuntimeException());
		} else if (!(element instanceof MDObject)) {
			return Either.left(Status.INVALID_ARGUMENT
				.withDescription(String.format("Element with ID %s is not an MDObject", id))
				.asRuntimeException());
		} else {
			return Either.right((MDObject) element);
		}
	}

	@Override
	public void ping(Empty request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, Either.right(Empty.newBuilder().build()));
	}

	@Override
	public void createInstance(CreateInstanceRequest request, StreamObserver<ModelElement> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> inSession(project)
			.flatMapRight((sessionManager) -> findEClassifier(request.getTypeName())
			.flatMapRight((eClassifier) -> {
					if (!(eClassifier instanceof EClass)) {
						return Either.left(exTypeNotInstantiable(String.format("%s is not an EClassifier", getFullyQualifiedName(eClassifier))));
					}

					EClass eClass = (EClass) eClassifier;
					if (eClass.isAbstract()) {
						return Either.left(exTypeNotInstantiable(String.format("%s is abstract", getFullyQualifiedName(eClass))));
					} else {
						final String methodName = "create" + eClass.getName() + "Instance";
						ElementsFactory factory = project.getElementsFactory();
						try {
							Method mCreateInstance = factory.getClass().getMethod(methodName);
							MDObject mdObject = (MDObject) mCreateInstance.invoke(factory);
							if (mdObject instanceof PackageableElement) {
								return findRootElement(request.getRootElementHyperlink(), project).flatMapRight((root) -> {
									try {
										ModelElementsManager.getInstance().addElement((Element) mdObject, (Element) root);
										return Either.right(encoder.encode(mdObject));
									} catch (ReadOnlyElementException e) {
										LOGGER.error(e.getMessage(), e);
										return Either.left(Status.INVALID_ARGUMENT
												.withDescription(String.format("Element with ID %s is read only", ((Element) root).getID()))
												.asRuntimeException());
									}
								});
							}

							return Either.right(encoder.encode(mdObject));
						} catch (NoSuchMethodException e) {
							LOGGER.error(e.getMessage(), e);
							return Either.left(exTypeNotInstantiable(String.format("Cannot find method %s in the ElementsFactory", methodName)));
						} catch (Exception e) {
							LOGGER.error(e.getMessage(), e);
							return Either.left(exTypeNotInstantiable(String.format("Invocation of method %s in the ElementsFactory failed", methodName)));
						}
					}
				})
			)
		));
	}

	@Override
	public void openSession(OpenSessionRequest request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> notInSession(project)
			.flatMapRight((sm) -> {
				sm.createSession(project, request.getDescription());
				return Either.right(Empty.newBuilder().build());
			})
		));
	}

	@Override
	public void closeSession(Empty request, StreamObserver<Empty> responseObserver) {
		interactWithOpenSession(responseObserver, (project) -> (sm) -> sm.closeSession(project));
	}

	@Override
	public void cancelSession(Empty request, StreamObserver<Empty> responseObserver) {
		interactWithOpenSession(responseObserver, (project) -> (sm) -> sm.cancelSession(project));
	}

	@Override
	public void deleteInstance(DeleteInstanceRequest request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> inSession(project)
			.flatMapRight((sessionManager) -> getMDObjectByID(project, request.getElementID())
			.flatMapRight((mdObject) -> {
				if (mdObject instanceof Element) {
					try {
						ModelElementsManager.getInstance().removeElement((Element) mdObject);
						return Either.right(Empty.newBuilder().build());
					} catch (ReadOnlyElementException e) {
						return Either.left(Status.INVALID_ARGUMENT
							.withDescription(String.format("Object with ID %s is read only", request.getElementID()))
							.asRuntimeException());
					}
				} else {
					return Either.left(Status.INVALID_ARGUMENT
						.withDescription(String.format("Object with ID %s is not an Element", request.getElementID()))
						.asRuntimeException());
				}
			})
		)));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setFeatureValue(SetFeatureValueRequest request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> inSession(project)
			.flatMapRight((sessionManager) -> getMDObjectByID(project, request.getElementID())
			.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getFeatureName())
			.flatMapRight((eFeature) -> {
				Object decoded;
				try {
					decoded = decoder.decode(project, eFeature, request.getNewValue());
				} catch (IllegalArgumentException ex) {
					return Either.left(Status.INVALID_ARGUMENT
						.withDescription(String.format("Could not decode value kind %s",
							request.getNewValue().getValueCase().name()))
						.asRuntimeException());
				}

				if (eFeature.isMany()) {
					// This mimics the EmfPropertySetter in the EMC EMF driver
					if (decoded instanceof Collection) {
						Collection<Object> targetCol = (Collection<Object>) mdObject.eGet(eFeature);
						Collection<Object> sourceCol = (Collection<Object>) decoded;
						targetCol.clear();
						targetCol.addAll(sourceCol);
					} else {
						return Either.left(Status.INVALID_ARGUMENT
							.withDescription(String.format(
								"Cannot assign a non-Collection to the many-valued %s feature in %s",
								eFeature.getName(), getFullyQualifiedName(mdObject.eClass())))
							.asRuntimeException());
					}
				} else {
					mdObject.eSet(eFeature, decoded);
				}
				return Either.right(Empty.newBuilder().build());
			})
		))));
	}

	@Override
	public void listSize(ProxyList request, StreamObserver<SingleInteger> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> getObjectByID(project, request.getElementID())
			.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getFeatureName())
			.flatMapRight((eFeature) -> getEList(mdObject, eFeature)
			.flatMapRight((eList) -> Either.right(SingleInteger.newBuilder().setValue(eList.size()).build())
		)))));
	}

	@Override
	public void listGet(ListPosition request, StreamObserver<Value> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> getObjectByID(project, request.getList().getElementID())
			.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getList().getFeatureName())
			.flatMapRight((eFeature) -> getEList(mdObject, eFeature)
			.flatMapRight((eList) -> {
				Value.Builder vb = Value.newBuilder();
				encoder.encode(mdObject, eFeature, vb, eList.get(request.getPosition()));
				return Either.right(vb.build());
			}))
		)));
	}

	@Override
	public void listSet(ListPositionValue request, StreamObserver<Value> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> inSession(project)
			.flatMapRight((sm) -> getMDObjectByID(project, request.getList().getElementID())
			.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getList().getFeatureName())
			.flatMapRight((eFeature) -> getEList(mdObject, eFeature)
			.flatMapRight((eList) -> {
				Object newValue = decoder.decode(project, eFeature, request.getValue());
				try {
					Object oldValue = eList.set(request.getPosition(), newValue);
					Value.Builder vb = Value.newBuilder();
					encoder.encode(mdObject, eFeature, vb, oldValue);
					return Either.right(Value.newBuilder().build());
				} catch (UnsupportedOperationException ex) {
					return Either.left(exListNotModifiable(mdObject, eFeature));
				}
			})))
		)));
	}

	@Override
	public void listAdd(ListPositionValue request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> inSession(project)
			.flatMapRight((sm) -> getMDObjectByID(project, request.getList().getElementID())
			.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getList().getFeatureName())
			.flatMapRight((eFeature) -> getEList(mdObject, eFeature)
			.flatMapRight((eList) -> {
				Object newValue = decoder.decode(project, eFeature, request.getValue());
				try {
					eList.add(request.getPosition(), newValue);
					return Either.right(Empty.newBuilder().build());
				} catch (UnsupportedOperationException ex) {
					return Either.left(exListNotModifiable(mdObject, eFeature));
				}
			})))
		)));
	}

	@Override
	public void listRemove(ListPosition request, StreamObserver<Value> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> inSession(project)
			.flatMapRight((sm) -> getMDObjectByID(project, request.getList().getElementID())
			.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getList().getFeatureName())
			.flatMapRight((eFeature) -> getEList(mdObject, eFeature)
			.flatMapRight((eList) -> {
				try {
					Object oldValue = eList.remove(request.getPosition());
					Value.Builder vb = Value.newBuilder();
					encoder.encode(mdObject, eFeature, vb, oldValue);
					return Either.right(vb.build());
				} catch (UnsupportedOperationException ex) {
					return Either.left(exListNotModifiable(mdObject, eFeature));
				}
			})))
		)));
	}

	@Override
	public void listMoveObject(ListPositionValue request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
				.flatMapRight((project) -> inSession(project)
				.flatMapRight((sm) -> getMDObjectByID(project, request.getList().getElementID())
				.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getList().getFeatureName())
				.flatMapRight((eFeature) -> getEList(mdObject, eFeature)
				.flatMapRight((eList) -> {
					try {
						Object toBeMoved = decoder.decode(project, eFeature, request.getValue());
						eList.move(request.getPosition(), toBeMoved);
						return Either.right(Empty.newBuilder().build());
					} catch (UnsupportedOperationException ex) {
						return Either.left(exListNotModifiable(mdObject, eFeature));
					}
				})))
			)));
	}

	@Override
	public void listClear(ProxyList request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
				.flatMapRight((project) -> inSession(project)
				.flatMapRight((sm) -> getMDObjectByID(project, request.getElementID())
				.flatMapRight((mdObject) -> getEFeature(mdObject.eClass(), request.getFeatureName())
				.flatMapRight((eFeature) -> getEList(mdObject, eFeature)
				.flatMapRight((eList) -> {
					try {
						eList.clear();
						return Either.right(Empty.newBuilder().build());
					} catch (UnsupportedOperationException ex) {
						return Either.left(exListNotModifiable(mdObject, eFeature));
					}
				})))
			)));
	}

	@Override
	public void openProject(ProjectLocation request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, ensureProjectIsActive(request));
	}

	private Either<StatusRuntimeException, Empty> ensureProjectIsActive(ProjectLocation request) {
		try {
			final URI projectURI = new URI(request.getFileURL());
			final ProjectsManager projectsManager = Application.getInstance().getProjectsManager();

			Optional<Project> project = findProjectByURI(projectURI);
			if (project.isPresent()) {
				projectsManager.setActiveProject(project.get());
			} else {
				final ProjectDescriptor descriptor = ProjectDescriptorsFactory.createProjectDescriptor(projectURI);
				final boolean silent = true;
				projectsManager.loadProject(descriptor, silent);

				project = findProjectByURI(projectURI);
				if (project.isPresent()) {
					projectsManager.setActiveProject(project.get());
				} else {
					return Either.left(Status.INVALID_ARGUMENT
						.withDescription(String.format("Failed to set project at %s as the active project", request.getFileURL()))
						.asRuntimeException());
				}
			}

			return Either.right(Empty.newBuilder().build());
		} catch (URISyntaxException ex) {
			return Either.left(Status.INVALID_ARGUMENT
				.withDescription(String.format("Invalid project URL %s", request.getFileURL()))
				.asRuntimeException());
		}
	}

	private Optional<Project> findProjectByURI(final URI projectURI) {
		final ProjectsManager projectsManager = Application.getInstance().getProjectsManager();
		for (Project p : projectsManager.getProjects()) {
			ProjectDescriptor loadedDescriptor = ProjectDescriptorsFactory.getDescriptorForProject(p);
			if (projectURI.equals(loadedDescriptor.getURI())) {
				return Optional.of(p);
			}
		}
		return Optional.absent();
	}

	@Override
	public void closeProject(Empty request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> {
				Application.getInstance().getProjectsManager().closeProject(project);
				return Either.right(Empty.newBuilder().build());
			}));
	}

	@Override
	public void saveProject(Empty request, StreamObserver<Empty> responseObserver) {
		sendResponse(responseObserver, inProject()
			.flatMapRight((project) -> {
				ProjectDescriptor pd = ProjectDescriptorsFactory.getDescriptorForProject(project);
				Application.getInstance().getProjectsManager().saveProject(pd, true);
				return Either.right(Empty.newBuilder().build());
			}));
	}

	private StatusRuntimeException exListNotModifiable(EObject mdObject, EStructuralFeature eFeature) {
		return Status.INVALID_ARGUMENT
			.withDescription(String.format("Feature %s in %s is not a modifiable list: it may be a derived feature", eFeature.getName(), getFullyQualifiedName(mdObject.eClass())))
			.asRuntimeException();
	}

	@SuppressWarnings("unchecked")
	private Either<StatusRuntimeException, EList<Object>> getEList(EObject mdObject, EStructuralFeature eFeature) {
		if (!eFeature.isMany()) {
			return Either.left(Status.INVALID_ARGUMENT
				.withDescription(String.format("Feature %s in %s is not many-valued", eFeature.getName(), getFullyQualifiedName(mdObject.eClass())))
				.asRuntimeException());
		} else {
			return Either.right((EList<Object>) mdObject.eGet(eFeature));
		}
	}

	private Either<StatusRuntimeException, EStructuralFeature> getEFeature(EClass eClassifier, String featureName) {
		final EStructuralFeature eFeature = eClassifier.getEStructuralFeature(featureName);
		if (eFeature == null) {
			return Either.left(Status.INVALID_ARGUMENT
				.withDescription(String.format("Feature %s does not exist in type %s",
					featureName, getFullyQualifiedName(eClassifier)))
				.asRuntimeException());
		} else {
			return Either.right(eFeature);
		}
	}

	private Either<StatusRuntimeException, EClassifier> findEClassifier(String typeName) {
		if (typeName != null && typeName.length() > 0) {
			Collection<EClassifier> options = ModelUtils.findEClassifier(typeName);
			if (options.isEmpty()) {
				return Either.left(exTypeNotFound(typeName));
			} else {
				return Either.right(options.iterator().next());
			}
		}
		return Either.right(null);
	}

	private Either<StatusRuntimeException, Collection<EEnum>> findEEnums(String typeName) {
		if (typeName != null && typeName.length() > 0) {
			Collection<EClassifier> options = ModelUtils.findEClassifier(typeName);
			final List<EEnum> eEnumOptions = options.stream()
					.filter(c -> (c instanceof EEnum))
					.map(c -> (EEnum) c)
					.collect(Collectors.toList());

			if (eEnumOptions.isEmpty()) {
				return Either.left(exTypeNotFound(typeName));
			} else {
				return Either.right(eEnumOptions);
			}
		}
		return Either.right(null);
	}

	private <T> void sendResponse(StreamObserver<T> responseObserver, Either<StatusRuntimeException, T> val) {
		val.apply((ex) -> {
			responseObserver.onError(ex);
		}, (result) -> {
			responseObserver.onNext(result);
			responseObserver.onCompleted();
		});
	}

	private <T> Either<StatusRuntimeException, Project> inProject() {
		Project project = Application.getInstance().getProject();
		if (project == null) {
			return Either.left(Status.FAILED_PRECONDITION
				.withDescription("Project is not open yet")
				.asRuntimeException());
		} else {
			return Either.right(project);
		}
	}

	private <T> Either<StatusRuntimeException, SessionManager> inSession(Project project) {
		return checkSession(project, true);
	}

	private <T> Either<StatusRuntimeException, SessionManager> notInSession(Project project) {
		return checkSession(project, false);
	}

	private <T> Either<StatusRuntimeException, SessionManager> checkSession(Project project, boolean expected) {
		final SessionManager sessionManager = SessionManager.getInstance();
		boolean inSession = sessionManager.isSessionCreated(project);
		if (inSession != expected) {
			return Either.left(Status.FAILED_PRECONDITION
				.withDescription(String.format("A session is %s", expected ? "not open yet" : "still open"))
				.asRuntimeException());
		} else {
			return Either.right(sessionManager);
		}
	}

	private void interactWithOpenSession(StreamObserver<Empty> responseObserver, Function<Project, Consumer<SessionManager>> call) {
		sendResponse(responseObserver, inProject().flatMapRight((project) -> {
			return inSession(project).flatMapRight((sm) -> {
				call.apply(project).accept(sm);
				return Either.right(Empty.newBuilder().build());
			});
		}));
	}

	private <T> StatusRuntimeException exTypeNotFound(final String typeName) {
		Metadata metadata = new Metadata();
		Metadata.Key<ErrorInfo> errorKey = ProtoUtils.keyForProto(ErrorInfo.getDefaultInstance());
		metadata.put(errorKey, ErrorInfo.newBuilder()
			.setReason(ModelServiceConstants.REASON_CANNOT_FIND_TYPE)
			.setDomain(GRPC_DOMAIN)
			.build());

		return Status.INVALID_ARGUMENT
			.withDescription(String.format("Cannot find type %s", typeName))
			.asRuntimeException(metadata);
	}
	
	private StatusRuntimeException exTypeNotInstantiable(final String description) {
		Metadata metadata = new Metadata();
		Metadata.Key<ErrorInfo> errorKey = ProtoUtils.keyForProto(ErrorInfo.getDefaultInstance());
		metadata.put(errorKey, ErrorInfo.newBuilder()
			.setReason(ModelServiceConstants.REASON_CANNOT_INSTANTIATE_TYPE)
			.setDomain(GRPC_DOMAIN)
			.build());
	
		return Status.INVALID_ARGUMENT.withDescription(description).asRuntimeException(metadata);
	}

}