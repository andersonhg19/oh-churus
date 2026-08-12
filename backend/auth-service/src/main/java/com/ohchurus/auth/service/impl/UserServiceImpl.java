package com.ohchurus.auth.service.impl;

import com.ohchurus.auth.dto.input.UserFilterDTO;
import com.ohchurus.auth.dto.input.UserRegisterDTO;
import com.ohchurus.auth.dto.input.UserSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;
import com.ohchurus.auth.dto.output.ResultUserDTO;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.mapper.UserMapper;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /**
     * Alta publica. No delega en saveAndUpdate a proposito: ese metodo decide
     * entre crear y actualizar mirando si viene "id", y por esa puerta se podia
     * secuestrar la cuenta de cualquiera sin estar autenticado. Aqui no hay
     * decision posible: siempre crea.
     */
    /** ¿El id es el del usuario autenticado? Sin sesion, siempre no. */
    private boolean esYo(Long id) {
        Long yo = com.ohchurus.auth.util.SecurityUtils.getAuthenticatedUserId();
        return yo != null && yo.equals(id);
    }

    @Override
    public ResultDTO register(UserRegisterDTO dto) {
        try {
            UserSaveDTO alta = new UserSaveDTO();
            alta.setName(dto.getName());
            alta.setEmail(dto.getEmail());
            alta.setPassword(dto.getPassword());
            alta.setBudgetStartDay(dto.getBudgetStartDay());
            return createUser(alta);
        } catch (Exception e) {
            log.error("Error registering user: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving user", 500);
        }
    }

    @Override
    public ResultDTO saveAndUpdate(UserSaveDTO dto) {
        try {
            boolean isUpdate = dto.getId() != null;

            if (isUpdate) {
                return updateUser(dto);
            } else {
                return createUser(dto);
            }
        } catch (Exception e) {
            log.error("Error saving user: {}", e.getMessage(), e);
            return new ResultDTO(false, "Error saving user", 500);
        }
    }

    private ResultDTO createUser(UserSaveDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            return new ResultDTO(false, "Password is required for new users", 101);
        }

        if (userRepository.existsByEmailAndActiveTrue(dto.getEmail())) {
            return new ResultDTO(false, "Email already in use", 102);
        }

        User user = User.builder()
                .name(dto.getName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .budgetStartDay(dto.getBudgetStartDay() != null ? dto.getBudgetStartDay() : 1)
                .active(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User created: {}", saved.getEmail());
        return new ResultDTO(userMapper.toResultDTO(saved));
    }

    private ResultDTO updateUser(UserSaveDTO dto) {
        /* Editar la cuenta de otro no es una operacion que exista en esta app.
           Se responde "no encontrado" y no "no puedes", para no confirmar que
           ese id existe. */
        if (!esYo(dto.getId())) {
            return new ResultDTO(false, "User not found", 103);
        }
        Optional<User> existing = userRepository.findByIdAndActiveTrue(dto.getId());
        if (existing.isEmpty()) {
            return new ResultDTO(false, "User not found", 103);
        }

        if (userRepository.existsByEmailAndActiveTrueAndIdNot(dto.getEmail(), dto.getId())) {
            return new ResultDTO(false, "Email already in use by another user", 102);
        }

        User user = existing.get();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());

        if (dto.getBudgetStartDay() != null) {
            user.setBudgetStartDay(dto.getBudgetStartDay());
        }

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        User saved = userRepository.save(user);
        log.info("User updated: {}", saved.getEmail());
        return new ResultDTO(userMapper.toResultDTO(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getById(Long id) {
        /* Solo tu propia cuenta. Antes cualquier autenticado leia la ficha de
           cualquiera probando ids consecutivos: nombre y correo de todos. */
        if (!com.ohchurus.auth.util.SecurityUtils.getAuthenticatedUserId().equals(id) && !esYo(id)) {
            return new ResultDTO(false, "User not found", 103);
        }
        Optional<User> user = userRepository.findByIdAndActiveTrue(id);
        if (user.isEmpty()) {
            return new ResultDTO(false, "User not found", 103);
        }
        return new ResultDTO(userMapper.toResultDTO(user.get()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getAll(UserFilterDTO filter) {
        /*
         * ESTO NO ES UN DIRECTORIO ABIERTO.
         *
         * Cualquier autenticado podia listar a TODOS los usuarios de la
         * plataforma con nombre y correo, y de paso con un LIKE: buscar "a"
         * los sacaba a todos.
         *
         * Se usa para UNA cosa legitima: invitar al nucleo familiar por correo
         * (budget-service pregunta por un correo concreto). Ese caso necesita
         * conocer el correo de antemano, que es exactamente el permiso social
         * que da una invitacion. Asi que:
         *   · sin correo en el filtro -> solo te devuelves a ti mismo,
         *   · con correo -> coincidencia EXACTA, nunca parcial.
         * Se pierde el listado libre, que no lo usaba ninguna pantalla.
         */
        String correoBuscado = filter.getEmail() == null ? null : filter.getEmail().trim();
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by("name").ascending());

        Page<User> page;
        if (correoBuscado == null || correoBuscado.isEmpty()) {
            Long yo = com.ohchurus.auth.util.SecurityUtils.getAuthenticatedUserId();
            List<User> soloYo = (yo == null)
                    ? List.of()
                    : userRepository.findByIdAndActiveTrue(yo).map(List::of).orElse(List.of());
            page = new org.springframework.data.domain.PageImpl<>(soloYo, pageable, soloYo.size());
        } else {
            List<User> exacto = userRepository.findByEmailAndActiveTrue(correoBuscado)
                    .map(List::of).orElse(List.of());
            page = new org.springframework.data.domain.PageImpl<>(exacto, pageable, exacto.size());
        }

        List<ResultUserDTO> list = page.getContent().stream()
                .map(userMapper::toResultDTO)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("page", page.getNumber());
        response.put("size", page.getSize());
        response.put("totalPage", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("list", list);

        return new ResultDTO(response);
    }

    @Override
    public ResultDTO delete(Long id) {
        /* Darse de baja es cosa de uno. Sin esta comprobacion, cualquier
           usuario autenticado desactivaba la cuenta de otro con solo su id, y
           el dueno se quedaba sin poder entrar sin saber por que. */
        if (!esYo(id)) {
            return new ResultDTO(false, "User not found", 103);
        }
        Optional<User> user = userRepository.findByIdAndActiveTrue(id);
        if (user.isEmpty()) {
            return new ResultDTO(false, "User not found", 103);
        }

        User entity = user.get();
        entity.setActive(false);
        userRepository.save(entity);
        log.info("User deleted (soft): {}", entity.getEmail());
        return new ResultDTO(true, "User deleted successfully", 0);
    }
}
