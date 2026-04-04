package com.ohchurus.auth.service.impl;

import com.ohchurus.auth.dto.input.UserFilterDTO;
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
        Optional<User> user = userRepository.findByIdAndActiveTrue(id);
        if (user.isEmpty()) {
            return new ResultDTO(false, "User not found", 103);
        }
        return new ResultDTO(userMapper.toResultDTO(user.get()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultDTO getAll(UserFilterDTO filter) {
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), Sort.by("name").ascending());

        Page<User> page = userRepository.findAllWithFilters(
                filter.getName(),
                filter.getEmail(),
                pageable);

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
