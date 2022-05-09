package lk.ijse.dep8.todo.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import lk.ijse.dep8.todo.dto.UserDTO;
import lk.ijse.dep8.todo.exception.ValidationException;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet(name = "LoginServlet", value = "/login/*")
public class LoginServlet extends HttpServlet {

    @Resource(name = "java:comp/env/jdbc/pool4todo")
    private volatile DataSource pool;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getContentType()==null || !req.getContentType().toLowerCase().startsWith("application/json")) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        String pathInfo = req.getPathInfo();

        if (pathInfo!=null && !pathInfo.equals("/")){
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            Jsonb jsonb = JsonbBuilder.create();
            UserDTO user = jsonb.fromJson(req.getReader(), UserDTO.class);

            if (user.getName() == null || !user.getName().matches("[A-Za-z ]+")) {
                throw new ValidationException("Invalid User name");
            } else if (user.getPassword()==null || !user.getPassword().matches(".+")) {
                throw new ValidationException("Invalid Password");
            }

            try(Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM user WHERE email=? AND password=?");
                stm.setString(1, user.getEmail());
                stm.setString(2, user.getPassword());

                if (stm.executeQuery().next()) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                }
            }
        } catch (JsonbException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (Throwable t) {
            t.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
