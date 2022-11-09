package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HTTPServlet2;
import lk.ijse.dep9.dto.BooksDTO;
import lk.ijse.dep9.dto.MemberDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
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
        if (request.getPathInfo()==null || request.getPathInfo().equals("/")){
            try{
                if (request.getContentType() == null || ! request.getContentType().startsWith("application/json")){
                    throw new JsonbException("Invalid Json File");
                }
                BooksDTO book = JsonbBuilder.create().fromJson(request.getReader(), BooksDTO.class);

                try(Connection connection1 = pool.getConnection()){
                    PreparedStatement dStm = connection1.prepareStatement("SELECT * FROM books WHERE isbn=?");
                    dStm.setString(1,book.getIsbn());
                    ResultSet rst1 = dStm.executeQuery();
                    if (rst1.next()){
                        response.sendError(HttpServletResponse.SC_CONFLICT,"ISBN Already in used! ");
                        return;
                    }
                }

                if (book.getIsbn()==null || !book.getIsbn().matches("\\d{13}")){
                    throw new JsonbException("Invalid ISBN Number");
                } else if (book.getTitle()==null || !book.getTitle().matches("^[A-Za-z0-9 ]+$")) {
                    throw new JsonbException("Title empty or Invalid !");
                } else if (book.getAuthor()==null || !book.getAuthor().matches("^[A-Za-z0-9 -]+$")) {
                    throw new JsonbException("Author empty or Invalid!");
                }

                try(Connection connection = pool.getConnection()){
                    PreparedStatement stm = connection.prepareStatement("INSERT INTO books (isbn, title, author, copies) VALUES (?,?,?,?)");
                    stm.setString(1,book.getIsbn());
                    stm.setString(2,book.getTitle());
                    stm.setString(3,book.getAuthor());
                    stm.setInt(4,book.getCopies());

                    int affectedRows = stm.executeUpdate();

                    if (affectedRows==1){
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setContentType("application/json");
                        response.setHeader("Access-Control-Allow-Origin","*");
                        JsonbBuilder.create().toJson(book,response.getWriter());
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to save the book");
                }

            }catch (JsonbException | SQLException e){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid JSON file");
                e.printStackTrace();
            }
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }


    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo()==null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid request!");
            return;
        }else {
            Matcher matcher = Pattern.compile("^/(\\d{13})/?$").matcher(request.getPathInfo());
            if (matcher.matches()){
                updateBook(matcher.group(1),request,response);
            }else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid book ID");
            }
        }
    }

    private void updateBook(String isbn, HttpServletRequest request, HttpServletResponse response) throws IOException {
            try{
                if (request.getContentType() == null || ! request.getContentType().startsWith("application/json")){
                    throw new JsonbException("Invalid Json File");
                }
                BooksDTO book = JsonbBuilder.create().fromJson(request.getReader(), BooksDTO.class);

                try(Connection connection1 = pool.getConnection()){
                    PreparedStatement dStm = connection1.prepareStatement("SELECT * FROM books WHERE isbn=?");
                    dStm.setString(1,isbn);
                    ResultSet rst1 = dStm.executeQuery();
                    if (!rst1.next()){
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST,"ISBN does not match! ");
                        return;
                    }
                }

                if (book.getIsbn()==null || !book.getIsbn().matches("^\\d{13}$") || !isbn.equals(book.getIsbn())){
                    throw new JsonbException("Invalid ISBN Number");
                } else if (book.getTitle()==null || !book.getTitle().matches("^[A-Za-z0-9 ]+$")) {
                    throw new JsonbException("Title empty or Invalid !");
                } else if (book.getAuthor()==null || !book.getAuthor().matches("^[A-Za-z0-9 -]+$")) {
                    throw new JsonbException("Author empty or Invalid!");
                }

                try(Connection connection = pool.getConnection()){
                    PreparedStatement stm = connection.prepareStatement("UPDATE books SET author=? , title=?, copies=? WHERE isbn=?");
                    stm.setString(1,book.getAuthor());
                    stm.setString(2,book.getTitle());
                    stm.setInt(3,book.getCopies());
                    stm.setString(4,isbn);

                    int affectedRows = stm.executeUpdate();

                    if (affectedRows==1){
                        response.setHeader("Access-Control-Allow-Origin","*");
                        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND,"Book does not exists");
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to save the book");
                }

            }catch (JsonbException | SQLException e){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid JSON file");
                e.printStackTrace();
            }

    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin","*");
        resp.setHeader("Access-Control-Allow-Methods","POST,GET,PATCH,HEAD,OPTIONS,PUT");


        String headers = req.getHeader("Access-Control-Request-Headers");
        if (headers !=null){
            resp.setHeader("Access-Control-Allow-Headers",headers);
            resp.setHeader("Access-Control-Expose-Headers",headers);
        }
    }
}
