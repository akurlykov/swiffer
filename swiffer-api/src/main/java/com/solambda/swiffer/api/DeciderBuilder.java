package com.solambda.swiffer.api;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.solambda.swiffer.api.internal.DeciderImpl;
import com.solambda.swiffer.api.internal.VersionedName;
import com.solambda.swiffer.api.internal.decisions.DecisionTaskPoller;
import com.solambda.swiffer.api.internal.decisions.WorkflowTemplate;
import com.solambda.swiffer.api.internal.decisions.WorkflowTemplateFactory;
import com.solambda.swiffer.api.internal.decisions.WorkflowTemplateRegistry;

/**
 * A builder of {@link Decider}.
 */
public class DeciderBuilder {
	private AmazonSimpleWorkflow swf;
	private String domain;
	private String identity;
	private String taskList;
	private List<Object> workflowTemplates;
	private WorkflowTemplateFactory templateFactory;

	public DeciderBuilder(final AmazonSimpleWorkflow swf, final String domain) {
		super();
		this.swf = swf;
		this.domain = domain;
		this.templateFactory = new WorkflowTemplateFactory();
	}

	/**
	 * @return a new instance of {@link Decider}
	 */
	public Decider build() {
		final DecisionTaskPoller poller = new DecisionTaskPoller(this.swf, this.domain, this.taskList, this.identity);
		final WorkflowTemplateRegistry registry = createWorkflowTemplateRegistry();
		return new DeciderImpl(poller, registry);
	}

	private WorkflowTemplateRegistry createWorkflowTemplateRegistry() {
		final Map<VersionedName, WorkflowTemplate> registry = new HashMap<>();
		for (final Object workflowTemplate : this.workflowTemplates) {
			createWorkflowTemplate(workflowTemplate, registry);
		}
		return new WorkflowTemplateRegistry(registry);
	}

	private void createWorkflowTemplate(
			final Object workflowTemplate,
			final Map<VersionedName, WorkflowTemplate> registry) {
		final VersionedName key = createVersionedName(workflowTemplate);
		final WorkflowTemplate value = createWorkflowTemplate(workflowTemplate);
		registry.put(key, value);
	}

	private WorkflowTemplate createWorkflowTemplate(final Object workflowTemplate) {
		return this.templateFactory.create(workflowTemplate);
	}

	private VersionedName createVersionedName(final Object workflowTemplate) {
		final WorkflowType workflowType = findWorkflowTypeAnnotation(workflowTemplate);
		return new VersionedName(workflowType.name(), workflowType.version());
	}

	private WorkflowType findWorkflowTypeAnnotation(final Object workflowTemplate) {
		final Annotation[] annotations = workflowTemplate.getClass().getAnnotations();
		for (final Annotation annotation : annotations) {
			final Class<? extends Annotation> annotationType = annotation.annotationType();
			final WorkflowType workflowType = annotationType.getAnnotation(WorkflowType.class);
			return workflowType;
		}
		throw new IllegalArgumentException(
				"The provided object " + workflowTemplate.getClass().getName()
						+ " has no workflow type information. Annotate it with a custom annotation which is itself"
						+ " annotated with " + WorkflowType.class.getName() + ".");
	}

	/**
	 * @param taskList
	 *            the task list to poll for decision tasks
	 * @return this builder
	 */
	public DeciderBuilder taskList(final String taskList) {
		this.taskList = taskList;
		return this;
	}

	/**
	 * Optional name of the decider
	 *
	 * @param identity
	 *            name of the Decider, for information
	 * @return this builder
	 */
	public DeciderBuilder identity(final String identity) {
		this.identity = identity;
		return this;
	}

	/**
	 * Required
	 *
	 * @param workflowTemplates
	 *            the templates that should handle business logic of polled
	 *            decision tasks
	 * @return this builder
	 */
	public DeciderBuilder workflowTemplates(final Object... workflowTemplates) {
		this.workflowTemplates = Arrays.asList(workflowTemplates);
		return this;
	}
}