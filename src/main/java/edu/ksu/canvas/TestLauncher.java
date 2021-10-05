package edu.ksu.canvas;

import edu.ksu.canvas.interfaces.*;
import edu.ksu.canvas.model.Course;
import edu.ksu.canvas.model.User;
import edu.ksu.canvas.model.assignment.*;
import edu.ksu.canvas.requestOptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

// Static imports to protect sensitive data
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

    private static final Logger LOG = LoggerFactory.getLogger(TestLauncher.class);
    private static HashMap<Long,Long> categoryMap = new HashMap<>(3);
    private static CanvasApiFactory apiFactory;
    private static CourseReader courseReader;
    private static Course ecrCourse;
    private static AssignmentReader assignmentReader;
    private static List<Assignment> assignments;
    private static AssignmentReader edAssingmentReader;
    private static List<Assignment> edAssignments;
    private static HashMap<Integer, Integer> userMap;
    private static SubmissionReader submissionReader;
    private static SubmissionReader edSubmissionReader;
    private static SubmissionWriter submissionWriter;
    private static List<Submission> submissions;
    private static List<Submission> edSubmissions;
    private static Map<String, MultipleSubmissionsOptions.StudentSubmissionOption> submissionsMap;

    public static void main(String[] args) {
        try {
            transferGrades();
        } catch (Exception var5) {
            LOG.error("Problem while executing example methods", var5);
        }
    }

    public static void initialize() throws IOException {
        apiFactory = new CanvasApiFactory(ECR_URL);
        courseReader = (CourseReader)apiFactory.getReader(CourseReader.class, ECR_TOKEN);
        ecrCourse = courseReader.getSingleCourse(new GetSingleCourseOptions(ECR_COURSE_ID)).get();

        LOG.info("ECR course found " + ecrCourse.getName());

        CanvasApiFactory edhesiveFactory = new CanvasApiFactory(PS_URL);
        CourseReader edhesiveCourseReader = (CourseReader) edhesiveFactory.getReader(CourseReader.class, PS_TOKEN);
        Course edhesiveCourse = edhesiveCourseReader.getSingleCourse(new GetSingleCourseOptions(PS_COURSE_ID)).get();

        LOG.info("Edhesive course found " + edhesiveCourse.getName());

        assignmentReader = (AssignmentReader) apiFactory.getReader(AssignmentReader.class, ECR_TOKEN);
        assignments = assignmentReader.listCourseAssignments(new ListCourseAssignmentsOptions(ecrCourse.getId().toString()));

        edAssingmentReader = (AssignmentReader) edhesiveFactory.getReader(AssignmentReader.class, PS_TOKEN);
        edAssignments = edAssingmentReader.listCourseAssignments(new ListCourseAssignmentsOptions(edhesiveCourse.getId().toString()));
        submissionReader = (SubmissionReader) apiFactory.getReader(SubmissionReader.class, ECR_TOKEN);
        edSubmissionReader = (SubmissionReader) edhesiveFactory.getReader(SubmissionReader.class, PS_TOKEN);

        submissionWriter = (SubmissionWriter) apiFactory.getWriter(SubmissionWriter.class, ECR_TOKEN);

        submissionsMap = new HashMap<String, MultipleSubmissionsOptions.StudentSubmissionOption>();

        categoryMap.put(PS_ACT_CATEGORY, ECR_ACT_CATEGORY);
        categoryMap.put(PS_ASSIGNMENT_CATEGORY, ECR_ASSIGNMENT_CATEGORY);
        categoryMap.put(PS_TEST_CATEGORY, ECR_TEST_CATEGORY);

        try {
            userMap = createUserMap();
        } catch (Exception e) {
            LOG.error("Unable to create User ID Map...");

        }
    }


    public static void transferGrades() throws IOException {
        initialize();

        /**
        try {
            quizCopier();
            LOG.info("Quizzes Successfully copied...");
        } catch (Exception e) {
            LOG.error("Unable to copy quizzes and transfer grades... ");
        }
        **/

        Collections.sort(edAssignments, Assignment.Comparators.NAME);
        Collections.sort(assignments, Assignment.Comparators.NAME);
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
        boolean retransfer = false;

        for (Assignment edAssignment : edAssignments) {
             int index = Collections.binarySearch(
                     assignments,
                     edAssignment,
                     Assignment.Comparators.NAME
             );
             Assignment assignment = index >= 0 ? assignments.get(index) : null;
             if (assignment != null) {
                 LOG.info("Matching Assignment Found: " + assignment.getName());
                 LOG.info("Category: " + assignment.getAssignmentGroupId());
                 edSubmissions = edSubmissionReader.getCourseSubmissions(
                         new GetSubmissionsOptions(PS_COURSE_ID, edAssignment.getId())
                         );
                 submissions = submissionReader.getCourseSubmissions(
                         new GetSubmissionsOptions(ECR_COURSE_ID, assignment.getId())
                 );
                 edSubmissions.removeIf((edSubmission) ->
                         edSubmission.getGrade() == null
                 );
                 for (Submission submission : submissions) {
                     edSubmissions.removeIf((edSubmission) ->
                             submission.getUserId().equals(userMap.get(edSubmission.getUserId()))
                             && submission.getGrade() != null
                             && submission.getScore() >= edSubmission.getScore()
                     );
                 }
                for (Submission edSubmission : edSubmissions)
                    ecrSubmissionWriter(edSubmission, assignment);
                if(!submissionsMap.isEmpty()) {
                    ecrPostSubmissions(assignment);
                }
                submissionsMap = new HashMap<String, MultipleSubmissionsOptions.StudentSubmissionOption>();
             } else {
                 ecrAssignmentCreator(edAssignment);
                 retransfer = true;
             }
        }
        LOG.info("\n====\nWill Re transfer Grades?: " + retransfer);
        if (retransfer) transferGrades();

    }

    public static void ecrAssignmentCreator(Assignment edAssignment) {
        AssignmentWriter assignmentWriter = (AssignmentWriter) apiFactory.getWriter(AssignmentWriter.class, ECR_TOKEN);
        LOG.info("Assignment match not Found: " + edAssignment.getName());
        edAssignment.setAssignmentGroupId(categoryMap.get(edAssignment.getAssignmentGroupId()));
        edAssignment.setPostToSis(true);

        try {
           assignmentWriter.createAssignment(ECR_COURSE_ID, edAssignment);
            LOG.info("Assignment created: " + edAssignment.getName());

        } catch (Exception e) {
            LOG.error("Assignment can't be created " + edAssignment.getName());
        }
    }

    public static void ecrSubmissionWriter(Submission edSubmission, Assignment assignment) {
        try {
            LOG.info("New score found for user: " + userMap.get(edSubmission.getUserId()));
            LOG.info("Score: " + edSubmission.getGrade());
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

    public static void ecrPostSubmissions(Assignment assignment) {
        try {
            submissionWriter.gradeMultipleSubmissionsByCourse(new MultipleSubmissionsOptions(
                            ECR_COURSE_ID, assignment.getId(), submissionsMap
                    )
            );
            LOG.info("Scores Written " + assignment.getName());
        } catch (Exception e) {
            LOG.error("Error Writing Grades : " + e.getMessage());
        }
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
