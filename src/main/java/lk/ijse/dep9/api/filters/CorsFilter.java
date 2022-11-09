package lk.ijse.dep9.api.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
@WebFilter(urlPatterns = {"/members/*","/books/*"})
public class CorsFilter extends HttpFilter {
    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (!req.getMethod().equalsIgnoreCase("OPTIONS")){

            res.addHeader("Access-Control-Allow-Origin","*");

            if (req.getParameter("page")!=null && req.getParameter("size")!=null){
                res.addHeader("Access-Control-Allow-headers","*");
                res.addHeader("Access-Control-Expose-headers","*");
            }
            chain.doFilter(req,res);

        }else {
            res.setHeader("Access-Control-Allow-Origin","*");
            res.setHeader("Access-Control-Allow-Methods","POST,GET,PATCH,HEAD,OPTIONS,PUT");


            String headers = req.getHeader("Access-Control-Request-Headers");
            if (headers !=null){
                res.setHeader("Access-Control-Allow-Headers",headers);
                res.setHeader("Access-Control-Expose-Headers",headers);
            }
        }

    }
}
