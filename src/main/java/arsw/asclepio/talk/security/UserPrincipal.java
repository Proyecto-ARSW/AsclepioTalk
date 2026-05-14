package arsw.asclepio.talk.security;

import java.util.UUID;

// Representa al usuario autenticado extraído del JWT de AsclepioM1
// Daniel Useche
public record UserPrincipal(
        UUID userId,
        String email,
        String rol,
        String nombre,
        String apellido,
        Integer hospitalId
) {
    public String fullName() {
        return nombre + " " + apellido;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(rol);
    }

    public boolean isMedico() {
        return "MEDICO".equals(rol);
    }

    public boolean isPaciente() {
        return "PACIENTE".equals(rol);
    }

    public boolean canCreateConversation() {
        return isAdmin() || isMedico();
    }

    public boolean canCensorMessage() {
        return isAdmin() || isMedico();
    }

    public boolean canManageCensoredWords() {
        return isAdmin();
    }
}
