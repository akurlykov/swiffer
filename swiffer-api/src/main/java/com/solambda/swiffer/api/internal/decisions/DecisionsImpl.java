package com.solambda.swiffer.api.internal.decisions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.simpleworkflow.model.*;
import com.google.common.base.Preconditions;
import com.solambda.swiffer.api.ActivityOptions;
import com.solambda.swiffer.api.Decisions;
import com.solambda.swiffer.api.WorkflowOptions;
import com.solambda.swiffer.api.duration.DurationTransformer;
import com.solambda.swiffer.api.internal.context.ActivityTaskFailedContext;
import com.solambda.swiffer.api.internal.handler.CloseWorkflowControl;
import com.solambda.swiffer.api.internal.utils.SWFUtils;
import com.solambda.swiffer.api.mapper.DataMapper;
import com.solambda.swiffer.api.retry.RetryControl;
import com.solambda.swiffer.api.retry.RetryPolicy;

public class DecisionsImpl implements Decisions {
	private static final Logger LOGGER = LoggerFactory.getLogger(DecisionsImpl.class);

	private List<Decision> decisions;
	private final DataMapper dataMapper;
	private final DurationTransformer durationTransformer;
	private final RetryPolicy globalRetryPolicy;

	public DecisionsImpl(DataMapper dataMapper, DurationTransformer durationTransformer, RetryPolicy globalRetryPolicy) {
		this.decisions = new ArrayList<>();
		this.dataMapper = dataMapper;
		this.durationTransformer = durationTransformer;
		this.globalRetryPolicy = globalRetryPolicy;
	}

	/**
	 * @return the list of decisions
	 */
	public List<Decision> get() {
		return Collections.unmodifiableList(this.decisions);
	}

	/**
	 * add a new decision to the list of deicisions for the given type, and
	 * return the decision to allow configuring it
	 *
	 * @param decisionType
	 * @return
	 */
	private Decision newDecision(final DecisionType decisionType) {
		final Decision decision = new Decision().withDecisionType(decisionType);
		this.decisions.add(decision);
		return decision;
	}

	/**
	 * @param object
	 * @return
	 */
	private String serialize(final Object object) {
		return dataMapper.serialize(object);
	}

	@Override
	public Decisions scheduleActivityTask(final Class<?> activityType, final Object input) {
		return doScheduleActivityTask(activityType, null, input, null);
	}

	@Override
	public Decisions scheduleActivityTask(final Class<?> activityType, final Object input, final String activityId,
			final ActivityOptions options) {
		return doScheduleActivityTask(activityType, activityId, input, options);
	}

	@Override
	public Decisions scheduleActivityTask(final Class<?> activityTypeClass, final Object input,
			final ActivityOptions options) {
		return doScheduleActivityTask(activityTypeClass, null, input, options);
	}

	@Override
	public Decisions scheduleActivityTask(final Class<?> activityType, final ActivityOptions options) {
		return doScheduleActivityTask(activityType, null, null, options);
	}

	@Override
	public Decisions retryActivity(Long scheduledEventId, ActivityTaskFailedContext context) {
		String activityName = context.activityType().name();
		return doRetryActivity(scheduledEventId, activityName, context, globalRetryPolicy);
	}

	@Override
	public Decisions retryActivity(Long scheduledEventId, ActivityTaskFailedContext context, RetryPolicy retryPolicy) {
		String activityName = context.activityType().name();
		return doRetryActivity(scheduledEventId, activityName, context, retryPolicy);
	}

	@Override
    public Decisions retryActivity(Long scheduledEventId, Class<?> activityClass, DecisionTaskContext context, RetryPolicy retryPolicy) {
        String activityName = toActivityType(activityClass).getName();
        return doRetryActivity(scheduledEventId, activityName, context, retryPolicy);
    }

    @Override
    public Decisions retryActivity(Long scheduledEventId, String activityName, DecisionTaskContext context, RetryPolicy retryPolicy) {
		return doRetryActivity(scheduledEventId, activityName, context, retryPolicy);
    }

    @Override
    public Decisions scheduleActivityTask(ActivityTaskScheduledEventAttributes source) {
        ScheduleActivityTaskDecisionAttributes attributes = new ScheduleActivityTaskDecisionAttributes()
                .withActivityType(source.getActivityType())
                .withActivityId(UUID.randomUUID().toString())
                .withInput(source.getInput())
                .withControl(source.getControl())
                .withTaskList(source.getTaskList())
                .withTaskPriority(source.getTaskPriority())
                .withHeartbeatTimeout(source.getHeartbeatTimeout())
                .withScheduleToCloseTimeout(source.getScheduleToCloseTimeout())
                .withScheduleToStartTimeout(source.getScheduleToStartTimeout())
                .withStartToCloseTimeout(source.getStartToCloseTimeout());

        newDecision(DecisionType.ScheduleActivityTask).withScheduleActivityTaskDecisionAttributes(attributes);
        return this;
    }

    private Decisions doScheduleActivityTask(final Class<?> activityTypeClass, final String activityId,
            final Object input,
            final ActivityOptions options) {
		ScheduleActivityTaskDecisionAttributes attributes = new ScheduleActivityTaskDecisionAttributes()
				.withActivityType(toActivityType(activityTypeClass))
				.withActivityId(activityId == null ? UUID.randomUUID().toString() : activityId)
				.withInput(serialize(input));
		if (options != null) {
			attributes = attributes
					.withControl(options.control())
					.withTaskList(options.taskList())
					.withTaskPriority(nullSafeToString(options.taskPriority()))
					.withHeartbeatTimeout(getActivityTimeout(options.getMaxHeartbeatDuration()))
					.withScheduleToCloseTimeout(getActivityTimeout(options.getScheduleToCloseDuration()))
					.withScheduleToStartTimeout(getActivityTimeout(options.getScheduleToStartDuration()))
					.withStartToCloseTimeout(getActivityTimeout(options.getStartToCloseDuration()));
		}
		newDecision(DecisionType.ScheduleActivityTask)
				.withScheduleActivityTaskDecisionAttributes(attributes);
		return this;
	}

	private ActivityType toActivityType(final Class<?> activityTypeClass) {
		Preconditions.checkArgument(activityTypeClass.isInterface());
		final com.solambda.swiffer.api.ActivityType activityType = activityTypeClass
				.getAnnotation(com.solambda.swiffer.api.ActivityType.class);
		Preconditions.checkState(activityType != null, "The interface %s, should be annotated with %s!",
				activityTypeClass, ActivityType.class);
		return new ActivityType().withName(activityType.name()).withVersion(activityType.version());
	}

	@Override
	public Decisions completeWorkflow() {
		return doCompleteWorkflow(null);
	}

	@Override
	public Decisions completeWorkflow(final Object result) {
		return doCompleteWorkflow(result);
	}

	@Override
	public Decisions cancelWorkflow(final String details) {
		return doCancelWorkflow(details);
	}

	@Override
	public Decisions failWorkflow(final String reason, final String details) {
		return doFailWorkflow(reason, details);
	}

	@Override
	public Decisions startTimer(final String timerId, final Duration duration) {
		return startTimer(timerId, duration, null);
	}

	@Override
	public Decisions startTimer(final String timerId, final Duration duration, final Object control) {

		newDecision(DecisionType.StartTimer)
				.withStartTimerDecisionAttributes(new StartTimerDecisionAttributes()
						.withTimerId(timerId)
						.withStartToFireTimeout(getTimerDuration(timerId, duration))
						.withControl(serialize(control)));
		return this;
	}

	@Override
	public Decisions cancelTimer(final String timerId) {
		newDecision(DecisionType.CancelTimer)
				.withCancelTimerDecisionAttributes(new CancelTimerDecisionAttributes()
						.withTimerId(timerId));
		return this;
	}

	@Override
    public Decisions recordMarker(String markerName, Object details) {
		checkMarkerName(markerName);
		return doRecordMarker(markerName, details);
    }

	@Override
    public Decisions recordMarker(String markerName) {
		checkMarkerName(markerName);
        return doRecordMarker(markerName, null);
    }

	@Override
	public Decisions startChildWorkflow(Class<?> workflowType, String workflowId) {
		return startChildWorkflow(workflowType, workflowId, null, null);
	}

	@Override
	public Decisions startChildWorkflow(Class<?> workflowType, String workflowId, Object input) {
		return startChildWorkflow(workflowType, workflowId, input, null);
	}

	@Override
	public Decisions startChildWorkflow(Class<?> workflowType, String workflowId, Object input, WorkflowOptions options) {
		SWFUtils.checkId(workflowId);
		Preconditions.checkNotNull(workflowType, "Workflow Type is required");

		WorkflowOptions params = SWFUtils.defaultIfNull(options, new WorkflowOptions());

		StartChildWorkflowExecutionDecisionAttributes attributes = new StartChildWorkflowExecutionDecisionAttributes()
				.withWorkflowType(SWFUtils.toSWFWorkflowType(workflowType))
				.withWorkflowId(workflowId)
				.withInput(serialize(input))
				.withExecutionStartToCloseTimeout(params.getMaxExecutionDuration())
				.withTaskList(params.getTaskList())
				.withTaskPriority(params.getTaskPriority())
				.withTaskStartToCloseTimeout(params.getMaxDecisionTaskDuration());
		if (params.getChildTerminationPolicy() != null){
			attributes.setChildPolicy(params.getChildTerminationPolicy());
		}


		newDecision(DecisionType.StartChildWorkflowExecution).withStartChildWorkflowExecutionDecisionAttributes(attributes);
		return this;
	}

	@Override
	public Decisions requestCancelExternalWorkflow(String workflowId, String runId) {
		return requestCancelExternalWorkflow(workflowId, runId, null);
	}

	@Override
	public Decisions requestCancelExternalWorkflow(String workflowId, String runId, Object control) {
		SWFUtils.checkId(workflowId);
		Preconditions.checkNotNull(runId, "Workflow Run ID is required");

		newDecision(DecisionType.RequestCancelExternalWorkflowExecution)
				.withRequestCancelExternalWorkflowExecutionDecisionAttributes(new RequestCancelExternalWorkflowExecutionDecisionAttributes()
																					  .withWorkflowId(workflowId)
																					  .withRunId(runId)
																					  .withControl(serialize(control)));
		return this;
	}

	@Override
	public Decisions continueAsNewWorkflow(String version) {
		// TODO: complete implementation in Issue #11
		return doContinueAsNewWorkflow(null, null, null, version);
	}

	@Override
	public String toString() {
		return "Decisions=" + this.decisions + "";
	}

	private String nullSafeToString(final Object object) {
		return object == null ? null : object.toString();
	}

	private String getTimerDuration(String timerId, Duration duration) {
		Duration transformed;
		if (duration == null) {
			LOGGER.warn("Required duration for timer {} was null, use ZERO duration instead.", timerId);
			transformed = durationTransformer.transform(Duration.ZERO);
		} else {
			transformed = durationTransformer.transform(duration);
		}

		return Long.toString(transformed.getSeconds());
	}

	private String getActivityTimeout(Duration duration) {
		if (duration == null) {
			return "NONE";
		}
		Duration transformed = durationTransformer.transform(duration);
		return Long.toString(transformed.getSeconds());
	}

	private Decisions doRetryActivity(Long scheduledEventId, String activityName, DecisionTaskContext context, RetryPolicy retryPolicy) {
		String timerId = RetryControl.getTimerId(activityName);
		RetryControl control = new RetryControl(scheduledEventId, activityName);
		int retries = context.getMarkerDetails(control.getMarkerName(), Integer.class).orElse(0);

		retryPolicy.durationToNextTry(++retries).ifPresent(duration -> startTimer(timerId, duration, control));
		return this;
	}

	private Decisions doRecordMarker(String markerName, Object details) {
		newDecision(DecisionType.RecordMarker)
				.withRecordMarkerDecisionAttributes(new RecordMarkerDecisionAttributes()
															.withMarkerName(markerName)
															.withDetails(serialize(details)));
		return this;
	}

	private Decisions doCompleteWorkflow(Object result) {
		doRecordMarker(CloseWorkflowControl.COMPLETE_MARKER, CloseWorkflowControl.completeWorkflowControl(result));
		newDecision(DecisionType.CompleteWorkflowExecution)
				.withCompleteWorkflowExecutionDecisionAttributes(
						new CompleteWorkflowExecutionDecisionAttributes()
								.withResult(serialize(result)));
		return this;
	}

	private Decisions doCancelWorkflow(String details) {
		doRecordMarker(CloseWorkflowControl.CANCEL_MARKER, CloseWorkflowControl.cancelWorkflowControl(details));
		newDecision(DecisionType.CancelWorkflowExecution)
				.withCancelWorkflowExecutionDecisionAttributes(new CancelWorkflowExecutionDecisionAttributes()
																	   .withDetails(details));
		return this;
	}

	private Decisions doFailWorkflow(String reason, String details) {
		doRecordMarker(CloseWorkflowControl.FAIL_MARKER, CloseWorkflowControl.failWorkflowControl(reason, details));
		newDecision(DecisionType.FailWorkflowExecution)
				.withFailWorkflowExecutionDecisionAttributes(new FailWorkflowExecutionDecisionAttributes()
																	 .withReason(reason)
																	 .withDetails(details));
		return this;
	}

	private Decisions doContinueAsNewWorkflow(Object input, WorkflowOptions options, Collection<String> tags, String version) {
		// TODO: complete in Issue #11
		WorkflowOptions params = SWFUtils.defaultIfNull(options, new WorkflowOptions());

		ContinueAsNewWorkflowExecutionDecisionAttributes attributes = new ContinueAsNewWorkflowExecutionDecisionAttributes();
		attributes.setInput(serialize(input));
		attributes.setExecutionStartToCloseTimeout(params.getMaxExecutionDuration());
		attributes.setTaskList(params.getTaskList());
		attributes.setTaskPriority(params.getTaskPriority());
		attributes.setTaskStartToCloseTimeout(params.getMaxDecisionTaskDuration());
		attributes.setTagList(tags);
		attributes.setWorkflowTypeVersion(version);
//		attributes.setLambdaRole("");
		if (params.getChildTerminationPolicy() != null) {
			attributes.setChildPolicy(params.getChildTerminationPolicy());
		}

		newDecision(DecisionType.ContinueAsNewWorkflowExecution).withContinueAsNewWorkflowExecutionDecisionAttributes(attributes);

		return this;
	}

	/**
	 * Ensure custom handler can not be specified for internal timer.
	 *
	 * @param markerName timer ID
	 * @return timer ID if acceptable
	 * @throws IllegalArgumentException if selected timer name is reserved for internal use
	 */
	private static String checkMarkerName(String markerName) {
		Preconditions.checkNotNull(markerName);
		Preconditions.checkArgument(!SWFUtils.startsWithAny(markerName, CloseWorkflowControl.CANCEL_MARKER,
															CloseWorkflowControl.COMPLETE_MARKER,
															CloseWorkflowControl.FAIL_MARKER), "This is reserved marker name");

		return markerName;
	}
}
