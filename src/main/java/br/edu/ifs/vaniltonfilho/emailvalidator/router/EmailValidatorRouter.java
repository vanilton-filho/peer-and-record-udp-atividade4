package br.edu.ifs.vaniltonfilho.emailvalidator.router;

import br.edu.ifs.vaniltonfilho.emailvalidator.router.model.EmailValidatorResponse;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email-validator")
public class EmailValidatorRouter {

    @GetMapping
    public ResponseEntity<EmailValidatorResponse> validate(@RequestParam String email) {
        // Usando o Apache Commons Valitador para a validação do email
        boolean isValid = EmailValidator.getInstance().isValid(email);
        // Criando o objeto que será serializado com o resultado da validação
        EmailValidatorResponse response = new EmailValidatorResponse();
        response.setResultValidator(isValid);

        System.out.println(email);
        System.out.println(response);
        // Retornando para quem iniciou a requisição;
        // Retornamos 200 OK com o payload contendo o resultado
        return ResponseEntity.ok(response);

    }
}
