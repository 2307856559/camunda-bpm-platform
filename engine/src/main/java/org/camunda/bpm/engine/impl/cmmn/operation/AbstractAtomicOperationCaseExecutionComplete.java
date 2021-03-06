/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.cmmn.operation;

import static org.camunda.bpm.engine.delegate.CaseExecutionListener.COMPLETE;
import static org.camunda.bpm.engine.impl.cmmn.execution.CaseExecutionState.COMPLETED;
import static org.camunda.bpm.engine.impl.util.ActivityBehaviorUtil.getActivityBehavior;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cmmn.behavior.CmmnActivityBehavior;
import org.camunda.bpm.engine.impl.cmmn.behavior.CompositeActivityBehavior;
import org.camunda.bpm.engine.impl.cmmn.behavior.TransferVariablesActivityBehavior;
import org.camunda.bpm.engine.impl.cmmn.execution.CmmnExecution;
import org.camunda.bpm.engine.impl.pvm.delegate.SubProcessActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl;

/**
 * @author Roman Smirnov
 *
 */
public abstract class AbstractAtomicOperationCaseExecutionComplete extends AbstractCmmnEventAtomicOperation {

  private static Logger log = Logger.getLogger(AbstractAtomicOperationCaseExecutionComplete.class.getName());

  protected String getEventName() {
    return COMPLETE;
  }

  protected CmmnExecution eventNotificationsStarted(CmmnExecution execution) {
    CmmnActivityBehavior behavior = getActivityBehavior(execution);
    triggerBehavior(behavior, execution);

    List<? extends CmmnExecution> children = execution.getCaseExecutions();
    if (children != null && !children.isEmpty()) {
      for (CmmnExecution child : children) {
        child.remove();
      }
    }

    execution.setCurrentState(COMPLETED);

    return execution;
  }

  protected void postTransitionNotification(CmmnExecution execution) {
    if (!execution.isCaseInstanceExecution()) {
      execution.remove();

    } else {
      CmmnExecution superCaseExecution = execution.getSuperCaseExecution();
      PvmExecutionImpl superExecution = execution.getSuperExecution();

      if (superCaseExecution != null) {
        TransferVariablesActivityBehavior behavior = (TransferVariablesActivityBehavior) getActivityBehavior(superCaseExecution);
        behavior.transferVariables(execution, superCaseExecution);
        superCaseExecution.complete();

      } else if (superExecution != null) {
        SubProcessActivityBehavior behavior = (SubProcessActivityBehavior) getActivityBehavior(superExecution);

        try {
          behavior.completing(superExecution, execution);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Error while completing sub case of case execution " + execution, e);
            throw e;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while completing sub case of case execution " + execution, e);
            throw new ProcessEngineException("Error while completing sub case of case execution " + execution, e);
        }

        // set sub case instance to null
        superExecution.setSubCaseInstance(null);

        try {
          behavior.completed(superExecution);
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Error while completing sub case of case execution " + execution, e);
            throw e;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while completing sub case of case execution " + execution, e);
            throw new ProcessEngineException("Error while completing sub case of case execution " + execution, e);
        }
      }

      execution.setSuperCaseExecution(null);
      execution.setSuperExecution(null);
    }

    CmmnExecution parent = execution.getParent();
    if (parent != null) {
      CmmnActivityBehavior behavior = getActivityBehavior(parent);
      if (behavior instanceof CompositeActivityBehavior) {
        CompositeActivityBehavior compositeBehavior = (CompositeActivityBehavior) behavior;
        compositeBehavior.handleChildCompletion(parent, execution);
      }
    }

  }

  protected abstract void triggerBehavior(CmmnActivityBehavior behavior, CmmnExecution execution);

}
