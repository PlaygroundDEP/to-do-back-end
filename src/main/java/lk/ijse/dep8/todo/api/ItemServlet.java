package lk.ijse.dep8.todo.api;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import lk.ijse.dep8.todo.dto.ItemDTO;
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

@WebServlet(name = "ItemServlet", value = "/items/*")
public class ItemServlet extends HttpServlet {

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo()!=null && !req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String query = req.getParameter("q");
        String email = req.getParameter("email");

        try(Connection connection = pool.getConnection()){
            boolean pagination = req.getParameter("page") !=null && req.getParameter("size")!=null;
            String sql = "SELECT * FROM item WHERE state=? AND user_email=?" + ((pagination) ? "LIMIT ? OFFSET ?":"");
            PreparedStatement stm = connection.prepareStatement(sql);
            PreparedStatement stmCount = connection.prepareStatement("SELECT count(*) FROM item WHERE state=? AND user_email=?");

            stm.setString(1,query);
            stm.setString(2,email);
            stmCount.setString(1,query);
            stmCount.setString(2,email);

            if (pagination) {
                int page = Integer.parseInt(req.getParameter("page"));
                int size = Integer.parseInt(req.getParameter("size"));

                stm.setInt(3, size);
                stm.setInt(4, (page-1)*size);
            }

            ResultSet rst = stm.executeQuery();
            List<ItemDTO> items = new ArrayList<>();

            while (rst.next()) {
                items.add((new ItemDTO(
                        rst.getInt("id"),
                        rst.getString("user_email"),
                        rst.getString("description"),
                        rst.getString("state")
                )));
            }

            resp.setContentType("application/json");
            ResultSet rstCount = stmCount.executeQuery();
            if (rstCount.next()) {
                resp.setHeader("X-Count", rstCount.getString(1));
            }

            Jsonb jsonb = JsonbBuilder.create();
            jsonb.toJson(items, resp.getWriter());

        } catch (Throwable e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo()==null || req.getPathInfo().equals("/")) {
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unable to delete all the items");
            return;
        } else if (req.getPathInfo()!=null && !req.getPathInfo().substring(1).matches("[0-9]+")) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Item not found");
            return;
        }

        int id = Integer.parseInt(req.getPathInfo().replaceAll("[/]", ""));

        try(Connection connection = pool.getConnection()) {
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM item WHERE id=?");
            stm.setInt(1, id);
            ResultSet rst = stm.executeQuery();
            if (rst.next()) {
                stm = connection.prepareStatement("DELETE FROM item WHERE id=?");
                stm.setInt(1, id);
                if (stm.executeUpdate()!=1){
                    throw new RuntimeException("Failed to delete the item");
                }
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Item does not exist");
            }
        } catch (Throwable e) {
            e.printStackTrace();
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
                pathInfo.substring(1).matches("[0-9]+"))){
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Item does not exist");
            return;
        }

        try{
            Jsonb jsonb = JsonbBuilder.create();
            ItemDTO item = jsonb.fromJson(req.getReader(), ItemDTO.class);

            if (method.equals("PUT")) {
                item.setId(Integer.parseInt(pathInfo.replaceAll("[/]", "")));
            }

            if ((item.getEmail() == null || !item.getEmail()
                    .matches("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"))){
                throw new ValidationException("Invalid Email");
            } else if (item.getDescription() == null || !item.getDescription().matches(".+")) {
                throw new ValidationException("Invalid Description");
            } else if (item.getState() == null || !(item.getState().equals("DONE") || item.getState().equals("NOT_DONE"))) {
                throw new ValidationException("Invalid State");
            }

            try(Connection connection = pool.getConnection()){
                PreparedStatement stm = connection.prepareStatement("SELECT * FROM user WHERE email=?");
                stm.setString(1, item.getEmail());

                if (!stm.executeQuery().next()) {
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "User does not exist");
                    return;
                } else {
                    if (method.equals("POST")) {
                        stm = connection.prepareStatement("INSERT INTO item (user_email, description, state) VALUES (?,?,?)");
                        stm.setString(1, item.getEmail());
                        stm.setString(2, item.getDescription());
                        stm.setString(3, item.getState());

                        if (stm.executeUpdate() != 1) {
                            throw new RuntimeException("Failed to save the item");
                        }
                        resp.setStatus(HttpServletResponse.SC_CREATED);
                    } else {
                        stm = connection.prepareStatement("UPDATE item SET description=?, state=? WHERE id=?");
                        stm.setString(1, item.getDescription());
                        stm.setString(2, item.getState());
                        stm.setInt(3, item.getId());

                        if (stm.executeUpdate() != 1) {
                            throw new RuntimeException("Failed Update the item");
                        }
                        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                }
            }

        } catch (JsonbException | ValidationException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (Throwable e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

}
