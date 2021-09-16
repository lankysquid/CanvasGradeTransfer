package edu.ksu.canvas;

import edu.ksu.canvas.interfaces.*;
import edu.ksu.canvas.model.Account;
import edu.ksu.canvas.model.Course;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.*;
import edu.ksu.canvas.requestOptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;

import static edu.ksu.canvas.Tokens.PS_TOKEN;
import static edu.ksu.canvas.Tokens.ECR_TOKEN;
import static edu.ksu.canvas.Tokens.PS_COURSE_ID;
import static edu.ksu.canvas.Tokens.ECR_COURSE_ID;
import static edu.ksu.canvas.Tokens.PS_URL;
import static edu.ksu.canvas.Tokens.ECR_URL;

import static edu.ksu.canvas.Tokens.ECR_ACT_CATEGORY;
import static edu.ksu.canvas.Tokens.PS_ACT_CATEGORY;
import static edu.ksu.canvas.Tokens.ECR_ASSIGNMENT_CATEGORY;
import static edu.ksu.canvas.Tokens.PS_ASSIGNMENT_CATEGORY;
import static edu.ksu.canvas.Tokens.ECR_TEST_CATEGORY;
import static edu.ksu.canvas.Tokens.PS_TEST_CATEGORY;
import static edu.ksu.canvas.Tokens.FIRST_SEM_ASSIGNMENTS;

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


    private static HashMap<Long,Long> categoryMap = new HashMap<>(3);
    private static final Logger LOG = LoggerFactory.getLogger(TestLauncher.class);

    public static void main(String[] args) {
        try {
            //getRootAccount();
            getOwnCourses();
        } catch (Exception var5) {
            LOG.error("Problem while executing example methods", var5);
        }
    }

    public static void getRootAccount() throws IOException {
        CanvasApiFactory apiFactory = new CanvasApiFactory(ECR_URL);
        AccountReader acctReader = (AccountReader) apiFactory.getReader(AccountReader.class, ECR_TOKEN);
        Account rootAccount = (Account)acctReader.getSingleAccount("1").get();
        LOG.info("Got account from Canvas: " + rootAccount.getName());
    }

    public static void getOwnCourses() throws IOException {
        CanvasApiFactory apiFactory = new CanvasApiFactory(ECR_URL);
        CourseReader courseReader = (CourseReader)apiFactory.getReader(CourseReader.class, ECR_TOKEN);
        Course ecrCourse = courseReader.getSingleCourse(new GetSingleCourseOptions(ECR_COURSE_ID)).get();

        LOG.info("ECR course found " + ecrCourse.getName());

        CanvasApiFactory edhesiveFactory = new CanvasApiFactory(PS_URL);
        CourseReader edhesiveCourseReader = (CourseReader) edhesiveFactory.getReader(CourseReader.class, PS_TOKEN);
        Course edhesiveCourse = edhesiveCourseReader.getSingleCourse(new GetSingleCourseOptions(PS_COURSE_ID)).get();

        LOG.info("Edhesive course found " + edhesiveCourse.getName());

        AssignmentReader assignmentReader = (AssignmentReader) apiFactory.getReader(AssignmentReader.class, ECR_TOKEN);
        List<Assignment> assignments = assignmentReader.listCourseAssignments(new ListCourseAssignmentsOptions(ecrCourse.getId().toString()));
        AssignmentWriter assignmentWriter = (AssignmentWriter) apiFactory.getWriter(AssignmentWriter.class, ECR_TOKEN);
        AssignmentReader edAssingmentReader = (AssignmentReader) edhesiveFactory.getReader(AssignmentReader.class, PS_TOKEN);
        List<Assignment> edAssignments = edAssingmentReader.listCourseAssignments(new ListCourseAssignmentsOptions(edhesiveCourse.getId().toString()));

        HashMap<Integer, Integer> userMap = null;

        try {
            userMap = createUserMap();
        } catch (Exception e) {
            LOG.error("Unable to create User ID Map...");

        }
        /**/
        //LOG.info(edUsers.get(0).getName());

        categoryMap.put(PS_ACT_CATEGORY, ECR_ACT_CATEGORY);
        categoryMap.put(PS_ASSIGNMENT_CATEGORY, ECR_ASSIGNMENT_CATEGORY);
        categoryMap.put(PS_TEST_CATEGORY, ECR_TEST_CATEGORY);
        SubmissionReader submissionReader = (SubmissionReader) apiFactory.getReader(SubmissionReader.class, ECR_TOKEN);
        SubmissionReader edSubmissionReader = (SubmissionReader) edhesiveFactory.getReader(SubmissionReader.class, PS_TOKEN);

        SubmissionWriter submissionWriter = (SubmissionWriter) apiFactory.getWriter(SubmissionWriter.class, ECR_TOKEN);

        List<Submission> submissions;
        List<Submission> edSubmissions;
        Map<String, MultipleSubmissionsOptions.StudentSubmissionOption> submissionsMap = new HashMap<String, MultipleSubmissionsOptions.StudentSubmissionOption>();
        try {
            quizCopier();
            LOG.info("Quizzes Successfully copied...");
        } catch (Exception e) {
            LOG.error("Unable to copy quizzes and transfer grades... ");
        }

        for(int i = 0; i < edAssignments.size(); i++) {
            // for spring assignments
            for (String springAssignment : FIRST_SEM_ASSIGNMENTS){
                if (!edAssignments.get(i).isPublished() || edAssignments.get(i).getName().startsWith(springAssignment)) {
                    edAssignments.remove(i);
                    i--;
                    break;
                }
            }
        }


        boolean found;
        /**/
        for (Assignment edAssignment : edAssignments) {
            found = false;
            for (Assignment assignment : assignments) {
                if (edAssignment.getName().equals(assignment.getName())) {
                    LOG.info("Matching Assignment Found: " + assignment.getName());
                    LOG.info("Category: " + assignment.getAssignmentGroupId());
                    found = true;
                    edSubmissions = edSubmissionReader.getCourseSubmissions(
                            new GetSubmissionsOptions(PS_COURSE_ID, edAssignment.getId())
                    );
                    submissions = submissionReader.getCourseSubmissions(
                            new GetSubmissionsOptions(ECR_COURSE_ID, assignment.getId())
                    );

                    for (Submission edSubmission : edSubmissions) {
                        boolean newScore = true;
                        for (Submission submission : submissions) {
                            if (submission.getUserId().equals(userMap.get(edSubmission.getUserId()))) {
                                if (edSubmission.getGrade() != null && submission.getGrade() != null) {
                                    if (submission.getScore() != edSubmission.getScore()) {
                                        newScore = false;
                                        submissions.remove(submission);
                                    }
                                }
                                break;
                            }
                        }
                        if(edSubmission.getGrade() != null && newScore){
                            LOG.info("New score found for user: " + userMap.get(edSubmission.getUserId()));
                            LOG.info("Score: " + edSubmission.getScore());
                            edSubmission.setMissing(false);

                            try {
                                submissionsMap.put(userMap.get(edSubmission.getUserId()).toString(),
                                        new MultipleSubmissionsOptions(ECR_COURSE_ID, assignment.getId(), null)
                                                .createStudentSubmissionOption("To be completed in Project Stem",
                                                        edSubmission.getGrade(),
                                                        false, false,
                                                        "", "")
                                );
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
                                    ECR_COURSE_ID, assignment.getId(), submissionsMap
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
                //edAssignment.setLockAt(edAssignment.getDueAt());
                edAssignment.setPostToSis(true);

                try {
                    assignmentWriter.createAssignment(ECR_COURSE_ID, edAssignment);
                    LOG.info("Assignment created: " + edAssignment.getName());

                } catch (Exception e) {
                    LOG.error("Assignment can't be created " + edAssignment.getName());
                }

            }
        }
        /**/

        /**/
    }

    public static void quizCopier() throws IOException {
        CanvasApiFactory apiFactory = new CanvasApiFactory(ECR_URL);
        CanvasApiFactory edhesiveFactory = new CanvasApiFactory(PS_URL);

        LOG.info("Retrieving Quizzes... ");

        QuizReader edQuizReader = (QuizReader) edhesiveFactory.getReader(QuizReader.class, PS_TOKEN);
        QuizReader quizReader = (QuizReader) apiFactory.getReader(QuizReader.class, ECR_TOKEN);

        QuizWriter quizWriter = (QuizWriter) apiFactory.getWriter(QuizWriter.class, ECR_TOKEN);

        List<Quiz> quizzes = quizReader.getQuizzesInCourse(ECR_COURSE_ID);
        List<Quiz> edQuizzes = edQuizReader.getQuizzesInCourse(PS_COURSE_ID);


        for(int i = 0; i < edQuizzes.size(); i++) {
            for (String springAssignment : FIRST_SEM_ASSIGNMENTS){
                if (!edQuizzes.get(i).getPublished()|| edQuizzes.get(i).getTitle().startsWith(springAssignment)) {
                    edQuizzes.remove(i);
                    i--;
                    break;
                }
            }
        }


        for(Quiz edQuiz : edQuizzes) {
            for(Quiz quiz : quizzes) {
                if(quiz.getTitle().equals(edQuiz.getTitle())) {
                    LOG.info("Matching quiz found: " + edQuiz.getTitle());
                    Integer edQuizId = edQuiz.getId();
                    edQuiz.setId(quiz.getId());
                    quizWriter.updateQuiz(edQuiz, ECR_COURSE_ID);
                    edQuiz.setId(edQuizId);
                    //transferQuizSubmissions(edQuiz, quiz);
                }
            }
        }

        LOG.info("Quizzes Updated");

    }

    private static void transferQuizSubmissions( Quiz edQuiz, Quiz quiz) throws IOException{

        CanvasApiFactory apiFactory = new CanvasApiFactory(ECR_URL);
        CanvasApiFactory edhesiveFactory = new CanvasApiFactory(PS_URL);

        HashMap<Integer,Integer> userMap = createUserMap();

        QuizSubmissionReader quizSubmissionReader = (QuizSubmissionReader) apiFactory.getReader(QuizSubmissionReader.class, ECR_TOKEN);
        QuizSubmissionReader edQuizSubmissionReader = (QuizSubmissionReader) edhesiveFactory.getReader(QuizSubmissionReader.class, PS_TOKEN);

        QuizSubmissionWriter quizsubmissionWriter = (QuizSubmissionWriter) apiFactory.getWriter(QuizSubmissionWriter.class, ECR_TOKEN);
        LOG.info("Retrieving ECR Quiz Submissions... ");
        List<QuizSubmission> quizSubmissions = quizSubmissionReader.getQuizSubmissions(ECR_COURSE_ID,quiz.getId().toString());
        LOG.info("Retrieving Edhesive Quiz Submissions... ");
        List<QuizSubmission> edQuizSubmissions = edQuizSubmissionReader.getQuizSubmissions(PS_COURSE_ID,edQuiz.getId().toString());

        LOG.info("Beginning Score Check...");
        for (QuizSubmission edSubmission : edQuizSubmissions) {
            boolean newScore = true;
            for (QuizSubmission submission : quizSubmissions) {
                if (submission.getUserId().equals(userMap.get(edSubmission.getUserId()))) {
                    if (edSubmission.getScore() != null && submission.getScore() != null) {
                        if (submission.getScore() <= edSubmission.getScore()) {
                            newScore = false;
                            LOG.info("Submission Removed: " + submission.getUserId());
                            quizSubmissions.remove(submission);
                        }
                    }
                    break;
                }
            }

            if(edSubmission.getScore() != null && newScore){
                LOG.info("New quiz score found for user: " + userMap.get(edSubmission.getUserId()));
                LOG.info("Score: " + edSubmission.getScore());
                Optional<QuizSubmission> submissionOptional = quizsubmissionWriter.startQuizSubmission(new StartQuizSubmissionOptions(ECR_COURSE_ID,quiz.getId()));
                if (submissionOptional.isPresent()) {
                    LOG.info("Creating new submission...");

                    /**
                    quizsubmissionWriter.completeQuizSubmission(
                            new CompleteQuizSubmissionOptions(
                                    ecrCourseId,
                                    quiz.getId(),
                                    submissionOptional.get().getSubmissionId(),
                                    submissionOptional.get().getAttempt(),
                                    submissionOptional.get().getValidationToken()
                            )
                    );
                     /**/
                }

            }
            /**/
        }
        LOG.info("Quiz Submissions: " + quizSubmissions.toString());
    }

    private static HashMap<Integer,Integer> createUserMap() throws  IOException{
        CanvasApiFactory apiFactory = new CanvasApiFactory(ECR_URL);

        CanvasApiFactory edhesiveFactory = new CanvasApiFactory(PS_URL);

        LOG.info("Retrieving users...");

        UserReader userReader = (UserReader) apiFactory.getReader(UserReader.class, ECR_TOKEN);
        List<User> users = userReader.getUsersInCourse(new GetUsersInCourseOptions(ECR_COURSE_ID));
        UserReader edUserReader = (UserReader) edhesiveFactory.getReader(UserReader.class, PS_TOKEN);
        List<User> edUsers = edUserReader.getUsersInCourse(new GetUsersInCourseOptions(PS_COURSE_ID));

        LOG.info("Creating User ID Map...");
        HashMap<Integer, Integer> userMap = new HashMap<>();
        for (User user : users) {
            for (int i = 0; i < edUsers.size(); i++) {
                User edUser = edUsers.get(i);
                if (edUser.getLoginId().equals(user.getEmail())) {
                    // LOG.info(user.getEmail());
                    userMap.put(edUser.getId(), user.getId());
                    edUsers.remove(i);
                    break;
                }
            }
        }
        return userMap;
    }
}
