package lk.ijse.dep9.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.db.ConnectionPool;

import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(name = "TempServlet", value = {"/release"})
public class TempServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

//        try(PrintWriter out = response.getWriter()){
//
//            out.printf("<style>\n" +
//                    "        p{\n" +
//                    "            font-size: 2rem;\n" +
//                    "            font-weight: bold;\n" +
//                    "        }\n" +
//                    "    </style>");
//
//            out.printf("<p>Request URI: %s</p><br>",request.getRequestURI());
//            out.printf("<p>Request URL: %s</p><br>",request.getRequestURL());
//            out.printf("<p>Request Servlet Path: %s</p><br>",request.getServletPath());
//            out.printf("<p>Request Context Path: %s</p><br>",request.getContextPath());
//            out.printf("<p>Request Path Info: %s</p><br>",request.getPathInfo());
//            out.printf("<p>getHttpServletMapping() : %s</p>",request.getHttpServletMapping());

//        }
        ConnectionPool pool =(ConnectionPool)getServletContext().getAttribute("pool");
        pool.releaseAllConnection();

    }
}
