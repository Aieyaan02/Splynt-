import java.io.Console;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Run via set-platform-password.sh; never accepts a password as a command argument. */
class SetPlatformPassword {
    public static void main(String[] args) throws Exception {
        Console console=System.console();
        if(console==null) throw new IllegalStateException("Run from your own interactive terminal; passwords are never read from chat or command arguments.");
        char[] password=console.readPassword("New platform admin password (10+ characters): ");
        char[] confirm=console.readPassword("Confirm password: ");
        if(password==null || confirm==null) return;
        try {
            String value=new String(password);
            if(!Arrays.equals(password,confirm) || value.length()<10 || value.getBytes(StandardCharsets.UTF_8).length>72)
                throw new IllegalArgumentException("Passwords must match, contain at least 10 characters and fit within 72 UTF-8 bytes.");
            Path path=Path.of(".env.platform.local");
            String config=Files.exists(path) ? Files.readString(path) : "SPLYNT_PLATFORM_BOOTSTRAP_ENABLED=false\nSPLYNT_PLATFORM_BOOTSTRAP_EMAIL=\n";
            config=config.replaceAll("(?m)^SPLYNT_PLATFORM_BOOTSTRAP_PASSWORD_HASH=.*\\R?", "");
            if(!config.endsWith("\n")) config+="\n";
            if(!Files.exists(path)) Files.createFile(path,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            Files.writeString(path,config+"SPLYNT_PLATFORM_BOOTSTRAP_PASSWORD_HASH="+new BCryptPasswordEncoder(12).encode(value)+"\n");
            console.printf("Password hash saved in ignored .env.platform.local. Activation remains a separate server configuration step.%n");
        } finally {Arrays.fill(password,'\0');Arrays.fill(confirm,'\0');}
    }
}
