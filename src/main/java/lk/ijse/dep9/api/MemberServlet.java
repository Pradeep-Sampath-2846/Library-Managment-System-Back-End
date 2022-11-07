package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.api.util.HTTPServlet2;
import lk.ijse.dep9.db.ConnectionPool;
import lk.ijse.dep9.dto.MemberDTO;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "MemberServlet", value = "/members/*",loadOnStartup = 0)
public class MemberServlet extends HTTPServlet2 {
    @Resource(lookup = "java:/comp/env/jdbc/dep9-lms")
    private DataSource pool;

//    @Override
//    public void init() throws ServletException {
//        try {
//            //initiate JNDI here
//            InitialContext ctx = new InitialContext();
//            pool = (DataSource)ctx.lookup("jdbc/lms");
//            System.out.println(pool);
//        } catch (NamingException e) {
//            throw new RuntimeException(e);
//        }
//    }

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
                    paginatedSearchAllMembers(query,Integer.parseInt(size),Integer.parseInt(page),response);
                }
            } else if (query!=null) {
                searchTextAllMembers(query,response);
            } else if (size!=null && page!=null) {
                if (!size.matches("\\d+") && !page.matches("\\d+")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid page or size");
                }else {
                    getPaginatedMembers(Integer.parseInt(size),Integer.parseInt(page),response);
                }
            }else {
                getAllMembers(response);
            }

        }else {
            Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}-([A-Fa-f0-9]{4}-){3}[A-Fa-f0-9]{12})/?$").
                    matcher(request.getPathInfo());
            if (matcher.matches()){
                getAMember(matcher.group(1),response);
            }else {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid UUID");
            }
        }
    }

    private void searchTextAllMembers(String query,HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()){
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?");
            query="%"+query+"%";
            for (int i = 1; i < 5; i++) {
                stm.setString(i,query);
            }
            ResultSet rst = stm.executeQuery();


            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                members.add(new MemberDTO(id,name,address,contact));

            }

            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members,response.getWriter());


        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to load the members");
        }

    }

    private void paginatedSearchAllMembers(String query,int size,int page,HttpServletResponse  response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            String sql = "SELECT COUNT(id) AS count FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ?";
            PreparedStatement stmCount = connection.prepareStatement(sql);
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id LIKE ? OR name LIKE ? OR address LIKE ? OR contact LIKE ? LIMIT ? OFFSET ?");

            int length =sql.split("[?]").length;
            query="%"+query+"%";

            for (int i = 1; i <= length; i++) {
                stmCount.setString(i,query);
                stm.setString(i,query);
            }

            stm.setInt(length+1,size);
            stm.setInt(length+2,(page-1)*size);

            ResultSet rst = stmCount.executeQuery();
            rst.next();
            response.setIntHeader("X-Total-Count",rst.getInt("count"));
            rst =stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while(rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                members.add(new MemberDTO(id,name,address,contact));

            }
            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-headers","*");
            response.addHeader("Access-Control-Expose-headers","*");
            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members,response.getWriter());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to load the members");
        }

    }
    private void getPaginatedMembers(int size,int page,HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            Statement stmCount = connection.createStatement();
            ResultSet rstCount = stmCount.executeQuery("SELECT COUNT(id) AS count FROM member");
            rstCount.next();
            response.setIntHeader("X-Total-Count",rstCount.getInt("count"));

            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member LIMIT ? OFFSET ?");
            stm.setInt(1,size);
            stm.setInt(2,(page-1)*size);

            ResultSet rst = stm.executeQuery();

            ArrayList<MemberDTO> members = new ArrayList<>();

            while (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                members.add(new MemberDTO(id,name,address,contact));

            }
            response.addHeader("Access-Control-Allow-Origin","*");
            response.addHeader("Access-Control-Allow-headers","*");
            response.addHeader("Access-Control-Expose-headers","*");
            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members,response.getWriter());


        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to load the members");
        }

    }
    private void getAllMembers(HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            Statement stm = connection.createStatement();
            ResultSet rst = stm.executeQuery("SELECT * FROM member");

            ArrayList<MemberDTO> members = new ArrayList<>();


            while (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                members.add(new MemberDTO(id,name,address,contact));


            }
            response.addHeader("Access-Control-Allow-Origin","*");
            Jsonb jsonb = JsonbBuilder.create();
            response.setContentType("application/json");
            jsonb.toJson(members,response.getWriter());


        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to lead the Members");
        }

    }
    private void getAMember(String memberID,HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM member WHERE id=? ");
            stm.setString(1,memberID);

            ResultSet rst = stm.executeQuery();
            if (rst.next()){
                String id = rst.getString("id");
                String name = rst.getString("name");
                String address = rst.getString("address");
                String contact = rst.getString("contact");

                response.setContentType("application/json");
                JsonbBuilder.create().toJson(new MemberDTO(id,name,address,contact),response.getWriter());

            }else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"Invalid member ID");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to load the members");
        }
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo()==null || request.getPathInfo().equals("/")){
            try{
                if (request.getContentType() == null || ! request.getContentType().startsWith("application/json")){
                    throw new JsonbException("Invalid Json File");
                }
                MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);
                if (member.getName()==null || !member.getName().matches("[A-Za-z ]+")){
                    throw new JsonbException("Name is empty or Invalid");
                } else if (member.getContact()==null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                    throw new JsonbException("Contact is empty of Invalid");
                } else if (member.getAddress()==null || !member.getAddress().matches("[A-Za-z0-9|,.:;#/\\ -]+")) {
                    throw new JsonbException("Address is empty or Invalid");
                }

                try(Connection connection = pool.getConnection()){
                    member.setId(UUID.randomUUID().toString());
                    PreparedStatement stm = connection.prepareStatement("INSERT INTO member (id, name, address, contact) VALUES (?,?,?,?)");
                    stm.setString(1,member.getId());
                    stm.setString(2,member.getName());
                    stm.setString(3,member.getAddress());
                    stm.setString(4,member.getContact());

                    int affectedRows = stm.executeUpdate();

                    if (affectedRows==1){
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        response.setContentType("application/json");
                        response.addHeader("Access-Control-Allow-Origin","*");
                        JsonbBuilder.create().toJson(member,response.getWriter());
                    }

                } catch (SQLException e) {
                    e.printStackTrace();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to save the member");
                }

            }catch (JsonbException e){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid JSON file");
            }
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }

    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getPathInfo()==null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid Request");
            return;
        }
        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}-([A-Fa-f0-9]{4}-){3}[A-Fa-f0-9]{12})/?$").
                matcher(request.getPathInfo());
        if (matcher.matches()){
            //Todo:Delete The members
            deleteMember(matcher.group(1),response );
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid UUID");
        }

    }

    private void deleteMember(String memberId,HttpServletResponse response) throws IOException {
        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("DELETE FROM member WHERE id=?");
            stm.setString(1,memberId);
            int affectedRows = stm.executeUpdate();
            if (affectedRows==0){
                response.sendError(HttpServletResponse.SC_NOT_FOUND,"Invalid member ID");
            }else {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to Delete the member");
        }
    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //members/{member-id} or members/{member-id}/
        if (request.getPathInfo()==null || request.getPathInfo().equals("/")){
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,"Invalid Request");
            return;
        }
        Matcher matcher = Pattern.compile("^/([A-Fa-f0-9]{8}-([A-Fa-f0-9]{4}-){3}[A-Fa-f0-9]{12})/?$").
                matcher(request.getPathInfo());
        if (matcher.matches()){
            updateMember(matcher.group(1),request,response );
        }else {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }


        //then check content type - if not content-type=JSON 400 bad request

        // then convert JSON file to  memberDTO (handle if not and send bad request)

        // prepared statement -> sql -> file -> executeUpdate=1(204 success not content) =0(404 )

    }

    private void updateMember(String memberID,HttpServletRequest request , HttpServletResponse response) throws IOException {

        try {
            if (request.getContentType()==null || !request.getContentType().startsWith("application/json")){
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid JSON");
                return;
            }

            MemberDTO member = JsonbBuilder.create().fromJson(request.getReader(), MemberDTO.class);
            if (member.getId()==null || !memberID.equalsIgnoreCase(member.getId())){
                throw new JsonbException("Id id empty or invalid");
            } else if (member.getName()==null || !member.getName().matches("[A-Za-z ]+")){
                throw new JsonbException("Name is empty or Invalid");
            } else if (member.getContact()==null || !member.getContact().matches("\\d{3}-\\d{7}")) {
                throw new JsonbException("Contact is empty of Invalid");
            } else if (member.getAddress()==null || !member.getAddress().matches("[A-Za-z0-9|,.:;#/\\ -]+")) {
                throw new JsonbException("Address is empty or Invalid");
            }

            try(Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("UPDATE member SET name =? , address =? , contact =? WHERE id =?");

                stm.setString(1,member.getName());
                stm.setString(2,member.getAddress());
                stm.setString(3,member.getContact());
                stm.setString(4,member.getId());

                if (stm.executeUpdate()==1){
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,"Member does not exists");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,"Failed to update the member");
            }
        } catch (JsonbException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,e.getMessage());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Access-Control-Allow-Origin","*");
        resp.setHeader("Access-Control-Allow-Methods","POST,GET,PATCH,DELETE,HEAD,OPTIONS,PUT");


        String headers = req.getHeader("Access-Control-Request-Headers");
        if (headers !=null){
            resp.setHeader("Access-Control-Allow-Headers",headers);
            resp.setHeader("Access-Control-Expose-Headers",headers);
        }
    }
}

