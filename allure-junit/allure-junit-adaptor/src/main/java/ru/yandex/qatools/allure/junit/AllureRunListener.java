package ru.yandex.qatools.allure.junit;

import org.junit.Ignore;
import org.junit.internal.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import ru.yandex.qatools.allure.Allure;
import ru.yandex.qatools.allure.events.*;
import ru.yandex.qatools.allure.utils.AnnotationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

/**
 * @author Dmitry Baev charlie@yandex-team.ru
 *         Date: 20.12.13
 */
public class AllureRunListener extends RunListener {

    private Allure lifecycle = Allure.LIFECYCLE;

    private final Map<String, String> suites = new HashMap<>();

    @Override
    public void testRunStarted(Description description) throws Exception {
        if (description == null) {
            // If you don't pass junit provider - surefire (<= 2.17) pass null instead of description
            return;
        }

        for (Description suite : description.getChildren()) {
            testSuiteStarted(suite);
        }
    }

    public void testSuiteStarted(Description description) {
        String uid = generateSuiteUid(description.getClassName());

        TestSuiteStartedEvent event = new TestSuiteStartedEvent(uid, description.getClassName());
        AnnotationManager am = new AnnotationManager(description.getAnnotations());

        am.update(event);

        getLifecycle().fire(event);
    }

    @Override
    public void testStarted(Description description) {
        TestCaseStartedEvent event = new TestCaseStartedEvent(getSuiteUid(description), description.getMethodName());
        AnnotationManager am = new AnnotationManager(description.getAnnotations());

        am.update(event);

        fireClearStepStorage();
        getLifecycle().fire(event);
    }

    @Override
    public void testFailure(Failure failure) {
        if (failure.getDescription().isTest()) {
            fireTestCaseFailure(failure.getException());
        } else {
            createFakeTestCaseWithFailure(failure.getDescription(), failure.getException());
        }
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        testFailure(failure);
    }

    @Override
    public void testIgnored(Description description) {
        createFakeTestCaseWithFailure(description, getIgnoredException(description));
    }

    @Override
    public void testFinished(Description description) {
        getLifecycle().fire(new TestCaseFinishedEvent());
    }


    public void testSuiteFinished(String uid) {
        getLifecycle().fire(new TestSuiteFinishedEvent(uid));
    }

    @Override
    public void testRunFinished(Result result) {
        for (String uid : getSuites().values()) {
            testSuiteFinished(uid);
        }
    }

    public String generateSuiteUid(String suiteName) {
        String uid = UUID.randomUUID().toString();
        synchronized (getSuites()) {
            getSuites().put(suiteName, uid);
        }
        return uid;
    }

    public String getSuiteUid(Description description) {
        String suiteName = description.getClassName();
        if (!getSuites().containsKey(suiteName)) {
            Description suiteDescription = Description.createSuiteDescription(description.getTestClass());
            testSuiteStarted(suiteDescription);
        }
        return getSuites().get(suiteName);
    }

    public AssumptionViolatedException getIgnoredException(Description description) {
        Ignore ignore = description.getAnnotation(Ignore.class);
        return ignore == null ? null : new AssumptionViolatedException(
                defaultIfEmpty(ignore.value(), "Test ignored (without reason)!")
        );
    }

    public void createFakeTestCaseWithFailure(Description description, Throwable throwable) {
        String uid = getSuiteUid(description);

        TestCaseStartedEvent event = new TestCaseStartedEvent(uid, description.getClassName());
        String methodName = description.getMethodName();
        event.setTitle(methodName == null ? description.getTestClass().getSimpleName() : methodName);

        fireClearStepStorage();
        getLifecycle().fire(event);

        fireTestCaseFailure(throwable);

        getLifecycle().fire(new TestCaseFinishedEvent());
    }

    public void fireTestCaseFailure(Throwable throwable) {
        if (throwable instanceof AssumptionViolatedException) {
            TestCaseSkippedEvent event = new TestCaseSkippedEvent();
            event.setThrowable(throwable);
            getLifecycle().fire(event);
        } else {
            TestCaseFailureEvent event = new TestCaseFailureEvent();
            event.setThrowable(throwable);
            getLifecycle().fire(event);
        }
    }

    public void fireClearStepStorage() {
        getLifecycle().fire(new ClearStepStorageEvent());
    }

    public Allure getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Allure lifecycle) {
        this.lifecycle = lifecycle;
    }

    public Map<String, String> getSuites() {
        return suites;
    }
}
