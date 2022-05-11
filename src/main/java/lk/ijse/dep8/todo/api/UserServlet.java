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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet(name = "UserServlet", value = "/users/*")
public class UserServlet extends HttpServlet {

    @Resource(name = "java:comp/env/jdbc/pool4todo")
    private volatile DataSource pool;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doSaveOrUpdate(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doSaveOrUpdate(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo()==null || req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unable to delete all the users");
            return;
        } else if (req.getPathInfo()!=null && !req.getPathInfo().substring(1).matches("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User not found");
            return;
        }

        String email = req.getPathInfo().replaceAll("[/]", "");

        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM user WHERE email=?");
            stm.setString(1, email);
            ResultSet rst = stm.executeQuery();
            if (rst.next()) {
                stm = connection.prepareStatement("DELETE FROM user WHERE email=?");
                stm.setString(1, email);
                if (stm.executeUpdate()!=1){
                    throw new RuntimeException("Failed to delete the user");
                }
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User does not exist");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo()!=null && !req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String query = req.getParameter("q");
        query = "%"+((query==null) ? "" : query)+"%";

        try(Connection connection = pool.getConnection()){
            boolean pagination = req.getParameter("page") !=null && req.getParameter("size")!=null;
            String sql = "SELECT * FROM user WHERE email LIKE ? OR name LIKE ?"+((pagination) ? "LIMIT ? OFFSET ?":"");
            PreparedStatement stm = connection.prepareStatement(sql);
            PreparedStatement stmCount = connection.prepareStatement("SELECT count(*) FROM user WHERE email LIKE ? OR name LIKE ?");

            stm.setString(1,query);
            stm.setString(2,query);
            stmCount.setString(1,query);
            stmCount.setString(2,query);

            if (pagination) {
                int page = Integer.parseInt(req.getParameter("page"));
                int size = Integer.parseInt(req.getParameter("size"));

                stm.setInt(3, size);
                stm.setInt(4, (page-1)*size);
            }

            ResultSet rst = stm.executeQuery();
            List<UserDTO> users = new ArrayList<>();

            while (rst.next()) {
                users.add((new UserDTO(
                        rst.getString("email"),
                        rst.getString("name")
                )));
            }

            resp.setContentType("application/json");
            ResultSet rstCount = stmCount.executeQuery();
            if (rstCount.next()) {
                resp.setHeader("X-Count", rstCount.getString(1));
            }

            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(users, resp.getWriter());

        } catch (SQLException e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void doSaveOrUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getContentType()==null || !req.getContentType().toLowerCase().startsWith("application/json")) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        String method = req.getMethod();
        String pathInfo = req.getPathInfo();

        if (method.equals("POST") && (pathInfo!=null && !pathInfo.equals("/"))){
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } else if (method.equals("PUT") && !(pathInfo != null &&
                pathInfo.substring(1).matches("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"))){
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "User does not exist");
            return;
        }

        try {
            Jsonb jsonb = JsonbBuilder.create();
            UserDTO user = jsonb.fromJson(req.getReader(), UserDTO.class);

            if (method.equals("PUT")) {
                user.setEmail(pathInfo.replaceAll("[/]", ""));
            }

            if (method.equals("POST") && (user.getEmail() == null || !user.getEmail()
                    .matches("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"))){
                throw new ValidationException("Invalid Email");
            } else if (user.getName() == null || !user.getName().matches("[A-Za-z ]+")) {
                throw new ValidationException("Invalid User name");
            } else if (user.getPassword()==null || !user.getPassword().matches(".+")) {
                throw new ValidationException("Invalid Password");
            }

            try(Connection connection = pool.getConnection()) {
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM user WHERE email=?");
                stm.setString(1, user.getEmail());

                if (stm.executeQuery().next()) {
                    if (method.equals("POST")){
                        resp.sendError(HttpServletResponse.SC_CONFLICT, "There is already exists an account registered with this email address");
                    } else{
                        stm = connection.prepareStatement("UPDATE user SET name=?, password=? WHERE email=?");
                        stm.setString(1, user.getName());
                        stm.setString(2, user.getPassword());
                        stm.setString(3, user.getEmail());
                        if (stm.executeUpdate() != 1) {
                            throw new RuntimeException("Failed to update the user information");
                        }
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                } else {
                    stm = connection.prepareStatement("INSERT INTO user (email, name, password) VALUES (?,?,?)");
                    stm.setString(1, user.getEmail());
                    stm.setString(2, user.getName());
                    stm.setString(3, user.getPassword());
                    if (stm.executeUpdate()!=1){
                        throw new RuntimeException("Failed to register the user, Try again!");
                    }
                    resp.setStatus(HttpServletResponse.SC_CREATED);
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
