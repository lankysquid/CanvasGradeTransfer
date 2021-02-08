package edu.ksu.canvas;

import edu.ksu.canvas.interfaces.*;
import edu.ksu.canvas.model.Account;
import edu.ksu.canvas.model.Course;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.Assignment;
import edu.ksu.canvas.model.assignment.Submission;
import edu.ksu.canvas.oauth.NonRefreshableOauthToken;
import edu.ksu.canvas.oauth.OauthToken;
import edu.ksu.canvas.requestOptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class with a main method that executes a couple of simple read-only requests
 * to the Canvas API. Intended as an example of how to use the library and an
 * easy place to write simple tests when developing.
 *
 * When executing the main method, you must pass in the Canvas base URL and a
 * manually generated API access token which you can get from your Canvas user
 * settings page.
 */
public class TestLauncher {

    private static final Long ECR_ACT_CATEGORY = 12243L;
    private static final Long ECR_QUIZ_CATEGORY = 14844L;
    private static final Long ECR_TEST_CATEGORY = 14845L;

    private static final Long ED_ACT_CATEGORY = 182709L;
    private static final Long ED_QUIZ_CATEGORY = 182710L;
    private static final Long ED_TEST_CATEGORY = 182711L;

    private static HashMap<Long,Long> categoryMap = new HashMap<>(3);


    private static final Logger LOG = LoggerFactory.getLogger(TestLauncher.class);
    private static String canvasUrl= "https://ecrchs.instructure.com";
    private static String edhesiveURL = "https://intro.edhesive.com";
    private static OauthToken oauthToken = new NonRefreshableOauthToken("11642~FMFmoasScCIU5Ns2oRucUHZPgOQl0qKIzzaEzJJVvKRQQnjWE7bmj8A6FSKC467N");
    private static OauthToken edhesiveToken = new NonRefreshableOauthToken("bZf6A7EoLQyKjxrr3DuKEWhXNfNTesF4TwAfjmdb13nVqzE4YLL0fQARh4FoKTBx");
    private static String ecrCourseId = "9968";
    private static String edhesiveCourseId = "46977";

    public static void main(String[] args) {
        try {
            //getRootAccount();
            getOwnCourses();
        } catch (Exception var5) {
            LOG.error("Problem while executing example methods", var5);
        }
    }

    public static void getRootAccount() throws IOException {
        CanvasApiFactory apiFactory = new CanvasApiFactory(canvasUrl);
        AccountReader acctReader = (AccountReader) apiFactory.getReader(AccountReader.class, oauthToken);
        Account rootAccount = (Account)acctReader.getSingleAccount("1").get();
        LOG.info("Got account from Canvas: " + rootAccount.getName());
    }

    public static void getOwnCourses() throws IOException {
        CanvasApiFactory apiFactory = new CanvasApiFactory(canvasUrl);
        CourseReader courseReader = (CourseReader)apiFactory.getReader(CourseReader.class, oauthToken);
        Course ecrCourse = courseReader.getSingleCourse(new GetSingleCourseOptions(ecrCourseId)).get();

        LOG.info("ECR course found " + ecrCourse.getName());

        CanvasApiFactory edhesiveFactory = new CanvasApiFactory(edhesiveURL);
        CourseReader edhesiveCourseReader = (CourseReader) edhesiveFactory.getReader(CourseReader.class, edhesiveToken);
        Course edhesiveCourse = edhesiveCourseReader.getSingleCourse(new GetSingleCourseOptions(edhesiveCourseId)).get();

        LOG.info("Edhesive course found " + edhesiveCourse.getName());

        AssignmentReader assignmentReader = (AssignmentReader) apiFactory.getReader(AssignmentReader.class, oauthToken);
        List<Assignment> assignments = assignmentReader.listCourseAssignments(new ListCourseAssignmentsOptions(ecrCourse.getId().toString()));
        AssignmentWriter assignmentWriter = (AssignmentWriter) apiFactory.getWriter(AssignmentWriter.class, oauthToken);
        AssignmentReader edAssingmentReader = (AssignmentReader) edhesiveFactory.getReader(AssignmentReader.class, edhesiveToken);
        List<Assignment> edAssignments = edAssingmentReader.listCourseAssignments(new ListCourseAssignmentsOptions(edhesiveCourse.getId().toString()));

        UserReader userReader = (UserReader) apiFactory.getReader(UserReader.class, oauthToken);
        List<User> users = userReader.getUsersInCourse(new GetUsersInCourseOptions(ecrCourse.getId().toString()));
        UserReader edUserReader = (UserReader) edhesiveFactory.getReader(UserReader.class, edhesiveToken);
        List<User> edUsers = edUserReader.getUsersInCourse(new GetUsersInCourseOptions(edhesiveCourse.getId().toString()));

        HashMap<Integer, Integer> userMap = new HashMap<>();
        for (User user : users) {
            for (int i = 0; i < edUsers.size(); i++) {
                User edUser = edUsers.get(i);
                if (edUser.getLoginId().equals(user.getEmail())) {
                    // LOG.info(user.getEmail());
                    userMap.put(edUser.getId(), user.getId());
                    edUsers.remove(i);
                }
            }
        }

        //LOG.info(edUsers.get(0).getName());

        categoryMap.put(ED_ACT_CATEGORY, ECR_ACT_CATEGORY);
        categoryMap.put(ED_QUIZ_CATEGORY, ECR_QUIZ_CATEGORY);
        categoryMap.put(ED_TEST_CATEGORY, ECR_TEST_CATEGORY);
        SubmissionReader submissionReader = (SubmissionReader) apiFactory.getReader(SubmissionReader.class, oauthToken);
        SubmissionReader edSubmissionReader = (SubmissionReader) edhesiveFactory.getReader(SubmissionReader.class, edhesiveToken);

        SubmissionWriter submissionWriter = (SubmissionWriter) apiFactory.getWriter(SubmissionWriter.class, oauthToken);

        List<Submission> submissions;
        List<Submission> edSubmissions;
        Map<String, MultipleSubmissionsOptions.StudentSubmissionOption> submissionsMap = new HashMap<String, MultipleSubmissionsOptions.StudentSubmissionOption>();

        boolean found;
        int runOnce = 1;
        Integer notFoundId = -1;

        for (Assignment edAssignment : edAssignments) {
            if (edAssignment.isPublished()) {
                notFoundId = edAssignment.getId();
                found = false;
                for (Assignment assignment : assignments) {
                    if (edAssignment.getName().equals(assignment.getName())) {
                        LOG.info("Matching Assignment Found: " + assignment.getName());
                        LOG.info("Category: " + assignment.getAssignmentGroupId());
                        found = true;
                        edSubmissions = edSubmissionReader.getCourseSubmissions(
                                new GetSubmissionsOptions(edhesiveCourseId, edAssignment.getId())
                        );
                        submissions = submissionReader.getCourseSubmissions(
                                new GetSubmissionsOptions(ecrCourseId, assignment.getId())
                        );
                        for (Submission edSubmission : edSubmissions) {
                            boolean newScore = true;
                            for (Submission submission : submissions) {
                                if (submission.getUserId().equals(userMap.get(edSubmission.getUserId()))) {
                                    if (edSubmission.getGrade() != null && submission.getGrade() != null) {
                                        if (submission.getScore() <= edSubmission.getScore()) {
                                            newScore = false;
                                            submissions.remove(submission);
                                        }
                                    }
                                    break;
                                }
                            }
                            if(edSubmission.getGrade() != null && newScore){
                                try {
                                    submissionsMap.put(userMap.get(edSubmission.getUserId()).toString(),
                                            new MultipleSubmissionsOptions(ecrCourseId, assignment.getId(), null)
                                                    .createStudentSubmissionOption("Autograder Passback",
                                                            edSubmission.getGrade(),
                                                            false, false,
                                                            "", "")
                                    );
                                    LOG.info("New score found for user: " + userMap.get(edSubmission.getUserId()));
                                    LOG.info("Score: " + edSubmission.getScore());
                                }
                                catch (NullPointerException e) {
                                    LOG.error("Error mapping student submissions. Likely user dropped course");
                                    LOG.error(e.getMessage());
                                }
                            }
                        }
                        if (!submissionsMap.isEmpty()) {
                            try {
                                submissionWriter.gradeMultipleSubmissionsByCourse(new MultipleSubmissionsOptions(
                                              ecrCourseId, assignment.getId(), submissionsMap
                                       )
                                );
                                LOG.info("Scores Written " + assignment.getName());
                            } catch (Exception e) {
                                LOG.error("Error Writing Grades : " + e.getMessage());
                            }
                            submissionsMap = new HashMap<String, MultipleSubmissionsOptions.StudentSubmissionOption>();
                        }
                    }
                }
                if (!found) {
                    LOG.info("Assignment match not Found: " + edAssignment.getName());
                    edAssignment.setAssignmentGroupId(categoryMap.get(edAssignment.getAssignmentGroupId()));
                    try {
                        assignmentWriter.createAssignment(ecrCourseId, edAssignment);
                        LOG.info("Assignment created: " + edAssignment.getName());
                    } catch (Exception e) {
                        LOG.error("Assignment can't be created " + edAssignment.getName());
                    }
                }
            }
        }
    }
}
