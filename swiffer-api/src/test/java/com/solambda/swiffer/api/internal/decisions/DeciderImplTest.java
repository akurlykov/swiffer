package com.solambda.swiffer.api.internal.decisions;

import static com.solambda.swiffer.test.Tests.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.model.AmazonSimpleWorkflowException;
import com.amazonaws.services.simpleworkflow.model.DecisionTask;
import com.amazonaws.services.simpleworkflow.model.EventType;
import com.amazonaws.services.simpleworkflow.model.HistoryEvent;
import com.amazonaws.services.simpleworkflow.model.PollForDecisionTaskRequest;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionStartedEventAttributes;
import com.solambda.swiffer.api.Decider;
import com.solambda.swiffer.api.OnWorkflowStarted;
import com.solambda.swiffer.api.Swiffer;
import com.solambda.swiffer.api.WorkflowType;
import com.solambda.swiffer.api.internal.VersionedName;
import com.solambda.swiffer.test.Tests;

public class DeciderImplTest {

	private static final String TASK_TOKEN = "token";
	private AmazonSimpleWorkflow swf;
	private Swiffer swiffer;
	private WorkflowTemplate1 workflowTemplate1;

	@WorkflowType(name = "workflowType1", version = "1")
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface WorkflowDef1 {

	}

	@WorkflowType(name = "workflowType2", version = "1")
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface WorkflowDef2 {

	}

	@WorkflowDef2
	public static class WorkflowTemplate2 {
		@OnWorkflowStarted
		public void started(final String input) {

		}
	}

	@WorkflowDef1
	public static class WorkflowTemplate1 {
		@OnWorkflowStarted
		public void started(final String input) {

		}
	}

	@Before
	public void setup() {
		this.swf = mock(AmazonSimpleWorkflow.class);
		this.swiffer = new Swiffer(this.swf, Tests.DOMAIN);
		this.workflowTemplate1 = spy(new WorkflowTemplate1());
	}

	private Decider createDecider() {
		return this.swiffer.newDeciderBuilder()
				.taskList("test-decision-task-list")
				.identity("decider-name")
				.workflowTemplates(this.workflowTemplate1)
				.build();
	}

	private void aDecisionTaskInTheTaskList() {
		final List<HistoryEvent> events = new ArrayList<>();

		events.add(new HistoryEvent()
				.withEventId(1L)
				.withEventType(EventType.WorkflowExecutionStarted)
				.withWorkflowExecutionStartedEventAttributes(
						new WorkflowExecutionStartedEventAttributes()
								.withInput("\"workflowInput\""))
				.withEventTimestamp(new Date()));

		when(this.swf.pollForDecisionTask(any(PollForDecisionTaskRequest.class)))
				.thenReturn(new DecisionTask()
						.withTaskToken(TASK_TOKEN)
						.withPreviousStartedEventId(0L)
						.withWorkflowType(new com.amazonaws.services.simpleworkflow.model.WorkflowType()
								.withName("workflowType1").withVersion("1"))
						.withEvents(events));
	}

	@Test
	public void aDeciderInvokePollForDecisionTask() {
		// GIVEN
		final Decider decider = createDecider();
		aDecisionTaskInTheTaskList();
		// WHEN
		decider.start();
		sleep(Duration.ofMillis(100));
		// THEN
		final ArgumentCaptor<PollForDecisionTaskRequest> captor = ArgumentCaptor
				.forClass(PollForDecisionTaskRequest.class);
		verify(this.swf, atLeastOnce()).pollForDecisionTask(captor.capture());
		final PollForDecisionTaskRequest request = captor.getValue();
		assertThat(request.getTaskList().getName()).isEqualTo("test-decision-task-list");
		assertThat(request.getIdentity()).isEqualTo("decider-name");
		assertThat(request.getDomain()).isEqualTo(Tests.DOMAIN);
	}

	@Test
	public void invokeDecisionExecutorWithDecisions() {
		// GIVEN
		final Decider decider = createDecider();
		aDecisionTaskInTheTaskList();

		// WHEN
		decider.start();
		sleep(Duration.ofMillis(100));
		// THEN
		final ArgumentCaptor<PollForDecisionTaskRequest> captor = ArgumentCaptor
				.forClass(PollForDecisionTaskRequest.class);
		verify(this.swf, atLeastOnce()).pollForDecisionTask(captor.capture());
		final PollForDecisionTaskRequest request = captor.getValue();
		assertThat(request.getTaskList().getName()).isEqualTo("test-decision-task-list");
		assertThat(request.getIdentity()).isEqualTo("decider-name");
		assertThat(request.getDomain()).isEqualTo(Tests.DOMAIN);
	}

	@Test
	public void theCorrectWorkflowTemplateIsInvoked() throws Exception {
		// GIVEN
		final DeciderImpl decider = (DeciderImpl) createDecider();
		aDecisionTaskInTheTaskList();
		// WHEN
		decider.pollAndExecuteTask();
		// THEN

		final ArgumentCaptor<String> captor = ArgumentCaptor
				.forClass(String.class);
		verify(this.workflowTemplate1).started(captor.capture());
		final String request = captor.getValue();
		assertThat(request).isEqualTo("workflowInput");
	}

	/**
	 * Do not throw exception if it is a SWF client exception.
	 */
	@Test
	public void executeTaskImmediately_ClientException() throws Exception {
		AmazonSimpleWorkflowException ex = new AmazonSimpleWorkflowException("A message");
		ex.setErrorType(ErrorType.Client);
		ex.setErrorCode("ValidationException");
		doThrow(ex).when(swf).respondDecisionTaskCompleted(any());

		DecisionTaskContext task = mock(DecisionTaskContext.class);
		when(task.workflowType()).thenReturn(new VersionedName("workflowType1", "1"));

		DeciderImpl decider = (DeciderImpl) createDecider();
		decider.executeTaskImmediately(task);
	}

	/**
	 * Throw exception if there is a SWF not client exception.
	 */
	@Test(expected = IllegalStateException.class)
	public void executeTaskImmediately_OtherException() throws Exception {
		AmazonSimpleWorkflowException ex = new AmazonSimpleWorkflowException("A message");
		ex.setErrorType(ErrorType.Service);
		doThrow(ex).when(swf).respondDecisionTaskCompleted(any());

		DecisionTaskContext task = mock(DecisionTaskContext.class);
		when(task.workflowType()).thenReturn(new VersionedName("workflowType1", "1"));

		DeciderImpl decider = (DeciderImpl) createDecider();
		decider.executeTaskImmediately(task);
	}
}
