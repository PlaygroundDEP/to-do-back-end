package lk.ijse.dep8.todo.dto;

public class ItemDTO {
    private int id;
    private String email;
    private String description;
    private String state;

    public ItemDTO() {
    }

    public ItemDTO(String email, String description, String state) {
        this.email = email;
        this.description = description;
        this.state = state;
    }

    public ItemDTO(int id, String email, String description, String state) {
        this.id = id;
        this.email = email;
        this.description = description;
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ItemDTO{" +
                "id='" + id + '\'' +
                ", email='" + email + '\'' +
                ", description='" + description + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}
