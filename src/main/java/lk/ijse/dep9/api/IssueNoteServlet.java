package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.*;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HTTPServlet2;
import lk.ijse.dep9.dto.IssueNoteDTO;
import lombok.Data;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

@WebServlet(name = "IssueNoteServlet", value = "/issue-notes")
public class IssueNoteServlet extends HTTPServlet2 {

    @Resource(lookup = "java:/comp/env/jdbc/dep9-lms")
    private DataSource pool;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            if (request.getPathInfo() !=null && !request.getPathInfo().equals("/")){
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
                return;
            }

            if (request.getContentType()==null || !request.getContentType().startsWith("application/json")){
                throw new JsonbException("Invalid JSON");
            }

            IssueNoteDTO issueNote = JsonbBuilder.create().fromJson(request.getReader(), IssueNoteDTO.class);
            createIssueNote(issueNote,response);

        } catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,e.getMessage());
            e.printStackTrace();
        }
    }

    private void createIssueNote(IssueNoteDTO issueNote, HttpServletResponse response) throws IOException {
        //Data Validation

        if (issueNote.getMemberId()==null || !issueNote.getMemberId().matches("[A-Fa-f0-9]{8}-([A-Fa-f0-9]{4}-){3}[A-Fa-f0-9]{12}")){
            throw new JsonbException("Member id is empty or invalid");
        }else if (issueNote.getBooks().isEmpty()){
            throw new JsonbException("Issue note requires at least one book");
        } else if (issueNote.getBooks().size()>3) {
            throw new JsonbException("Member can't borrow more than 3 books");
        } else if (!issueNote.getBooks().stream().allMatch(isbn-> isbn.matches("\\d{13}"))) {
            throw new JsonbException("Invalid ISBN has been found");
        } else if (issueNote.getBooks().stream().collect(Collectors.toSet()).size() != issueNote.getBooks().size()) {
            throw new JsonbException("Duplicate ISBN found");
        }

        //Business Validation


        try(Connection connection = pool.getConnection()) {
            PreparedStatement stmExist = connection.prepareStatement("SELECT id FROM member WHERE id =?");
            stmExist.setString(1,issueNote.getMemberId());
            if (!stmExist.executeQuery().next()){
                throw new JsonbException("Member does not exist within the database");
            }

            PreparedStatement stm = connection.prepareStatement("SELECT b.title,b.copies,(b.copies-COUNT(ii.isbn)>0) AS availability FROM books b\n" +
                    "LEFT JOIN issue_item ii on b.isbn = ii.isbn\n" +
                    "LEFT JOIN `return` r ON ii.issue_id = r.issue_id and ii.isbn = r.isbn\n" +
                    "WHERE r.issue_id IS NULL AND b.isbn=? group by b.isbn ;");

            PreparedStatement stmDuplicateExist = connection.prepareStatement("SELECT m.name,member_id,ii.isbn FROM issue_item ii\n" +
                    "INNER JOIN issue_note `in` on ii.issue_id = `in`.id\n" +
                    "RIGHT JOIN member m on `in`.member_id = m.id\n" +
                    "LEFT JOIN `return`r ON ii.issue_id = r.issue_id and ii.isbn = r.isbn\n" +
                    "WHERE r.date IS NULL AND m.id=? AND ii.isbn=?");

            stmDuplicateExist.setString(1, issueNote.getMemberId());




            for (String isbn : issueNote.getBooks()) {
                stm.setString(1,isbn);
                stmDuplicateExist.setString(2,isbn);
                ResultSet rst2 = stmDuplicateExist.executeQuery();
                ResultSet rst = stm.executeQuery();
                if (!rst.next()) throw new JsonbException(isbn+" book doesn't exist");

                if (!rst.getBoolean("availability")){
                    throw new JsonbException(isbn+" book is not available at the moment");
                }
                if (rst2.next()) throw new JsonbException(isbn+" book has been already issued to  the same member");
            }
            PreparedStatement stm2 = connection.prepareStatement("SELECT m.id,m.name,3-COUNT(in.id) as available FROM member m\n" +
                    "LEFT JOIN issue_note `in` on m.id = `in`.member_id\n" +
                    "LEFT JOIN issue_item ii on `in`.id = ii.issue_id\n" +
                    "LEFT JOIN `return` r On ii.issue_id = r.issue_id and ii.isbn = r.isbn\n" +
                    "WHERE r.date IS NULL AND m.id=? group by m.id");

            stm2.setString(1,issueNote.getMemberId());

            ResultSet rst = stm2.executeQuery();
            rst.next();
            int available = rst.getInt("available");

            if (available<issueNote.getBooks().size()){
                throw new JsonbException("Issue limit is exceeded,only "+available+ " books are available");
            }

            try{
                connection.setAutoCommit(false);

                PreparedStatement stmIssueNote = connection.prepareStatement("INSERT INTO issue_note (date, member_id) VALUES (?,?)",Statement.RETURN_GENERATED_KEYS);

                stmIssueNote.setDate(1,Date.valueOf(LocalDate.now()));
                stmIssueNote.setString(2,issueNote.getMemberId());

                if(stmIssueNote.executeUpdate()!=1) throw new SQLException("Failed to insert the issue note");

                ResultSet generatedKeys = stmIssueNote.getGeneratedKeys();
                generatedKeys.next();
                int issueNoteId = generatedKeys.getInt(1);

                PreparedStatement stnIssueItem = connection.prepareStatement("INSERT INTO issue_item (issue_id, isbn) VALUES (?,?)");
                stnIssueItem.setInt(1,issueNoteId);

                for (String isbn : issueNote.getBooks()) {
                    stnIssueItem.setString(2,isbn);

                    if (stnIssueItem.executeUpdate()!=1) throw new SQLException("Failed to insert the issue item");
                }

                connection.commit();
                issueNote.setId(issueNoteId);
                issueNote.setDate(LocalDate.now());
                response.setContentType("application/json");
                JsonbBuilder.create().toJson(issueNote,response.getWriter());
            }catch (Throwable t){
                t.printStackTrace();
                connection.rollback();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to place the issue note");

            } finally {
                connection.setAutoCommit(true);
            }



        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to place the issue note");
        }


    }
}
