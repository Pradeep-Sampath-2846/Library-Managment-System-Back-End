package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HTTPServlet2;
import lk.ijse.dep9.dto.BooksDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "BookServlet", value = "/books/*")
public class BookServlet extends HTTPServlet2 {
    @Resource(lookup = "java:/comp/env/jdbc/dep9-lms")
    private DataSource pool;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo()==null || request.getPathInfo().equals("/")){
            String query = request.getParameter("q");
            String size = request.getParameter("size");
            String page = request.getParameter("page");
            if (query!=null && size !=null && page !=null){
                if (!size.matches("\\d+") && !page.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"invalid page or size");
                }else {
                    paginatedSearchBooks(query,Integer.parseInt(size),Integer.parseInt(page),response);
                }
            } else if (query!=null) {
                searchAllBooks(query,response);
            } else if (size!=null && page!=null) {
                if (!size.matches("\\d+") && !page.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid page or size");
                }else {
                    getPaginatedBooks(Integer.parseInt(size),Integer.parseInt(page),response);
                }
            }else {
                getAllBooks(response);
            }

        }else {
            Matcher matcher = Pattern.compile("^/(\\d{13})/?$").
                    matcher(request.getPathInfo());
            if (matcher.matches()){
                getBook(matcher.group(1),response);
            }else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid ISBN");
            }
        }
    }

    private void getBook(String isbn, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM books WHERE isbn=?");
            stm.setString(1,isbn);

            ResultSet rst = stm.executeQuery();
            if (rst.next()){
                String isbn1 = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin","*");
                Jsonb jsonb = JsonbBuilder.create();
                jsonb.toJson(new BooksDTO(isbn1,title,author,copies),response.getWriter());
            }else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"Invalid Member ID");
            }



        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }

    private void getAllBooks(HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT *FROM books");

            ArrayList<BooksDTO> books = new ArrayList<>();
            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                books.add(new BooksDTO(isbn,title,author,copies));
            }

            response.setHeader("Access-Control-Allow-Origin","*");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }

    private void getPaginatedBooks(int size, int page, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            Statement stmCount = connection.createStatement();
            ResultSet rstCount = stmCount.executeQuery("SELECT COUNT(isbn) AS count FROM books");
            rstCount.next();
            response.setIntHeader("X-Total-Count",rstCount.getInt("count"));

            PreparedStatement stm = connection.prepareStatement("SELECT *FROM books LIMIT ? OFFSET ?");
            stm.setInt(1,size);
            stm.setInt(2,(page-1)*size);

            ResultSet rst = stm.executeQuery();
            ArrayList<BooksDTO> books = new ArrayList<>();

            while(rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                books.add(new BooksDTO(isbn,title,author,copies));
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-headers","*");
            response.addHeader("Access-Control-Expose-headers","*");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        }

    }

    private void searchAllBooks(String query, HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT *FROM books WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ?");
            query ="%"+query+"%";
            for (int i = 1; i < 4; i++) {
                stm.setString(i,query);
            }
            ResultSet rst = stm.executeQuery();

            ArrayList<BooksDTO> books = new ArrayList<>();

            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                books.add(new BooksDTO(isbn,title,author,copies));

            }

            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }

    private void paginatedSearchBooks(String query, int size, int page, HttpServletResponse response) throws IOException {

        try(Connection connection = pool.getConnection()) {
            String sql = "SELECT COUNT(isbn) AS count FROM books WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ?";
            PreparedStatement stmCount = connection.prepareStatement(sql);
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM books WHERE isbn LIKE ? OR title LIKE ? OR author LIKE ? LIMIT ? OFFSET ?");
            query="%"+query+"%";

            for (int i = 1; i < 4; i++) {
                stmCount.setString(i,query);
                stm.setString(i,query);
            }

            stm.setInt(4,size);
            stm.setInt(5,(page-1)*size);
            ResultSet rstCount = stmCount.executeQuery();
            rstCount.next();
            response.setIntHeader("X-Total-Count",rstCount.getInt("count"));

            ResultSet rst = stm.executeQuery();
            ArrayList<BooksDTO> books = new ArrayList<>();
            while (rst.next()){
                String isbn = rst.getString("isbn");
                String title = rst.getString("title");
                String author = rst.getString("author");
                int copies = rst.getInt("copies");

                books.add(new BooksDTO(isbn,title,author,copies));
            }

            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-headers","*");
            response.addHeader("Access-Control-Expose-headers","*");
            response.setContentType("application/json");
            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(books,response.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"here is the error");
        }

    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.getWriter().println("BookServlet doPost()");

    }

    @Override
    protected void doPatch(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        response.getWriter().println("BookServlet doPatch()");
    }
}
