package com.ohchurus.auth.service;

import com.ohchurus.auth.dto.input.UserFilterDTO;
import com.ohchurus.auth.dto.input.UserSaveDTO;
import com.ohchurus.auth.dto.output.ResultDTO;
import com.ohchurus.auth.dto.output.ResultUserDTO;
import com.ohchurus.auth.entity.User;
import com.ohchurus.auth.mapper.UserMapper;
import com.ohchurus.auth.repository.UserRepository;
import com.ohchurus.auth.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private ResultUserDTO testResultDTO;
    private UserSaveDTO testSaveDTO;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@ohchurus.com")
                .password("encoded-password")
                .budgetStartDay(1)
                .active(true)
                .build();

        testResultDTO = new ResultUserDTO();
        testResultDTO.setId(1L);
        testResultDTO.setName("Test User");
        testResultDTO.setEmail("test@ohchurus.com");
        testResultDTO.setBudgetStartDay(1);
        testResultDTO.setActive(true);

        testSaveDTO = new UserSaveDTO();
        testSaveDTO.setName("Test User");
        testSaveDTO.setEmail("test@ohchurus.com");
        testSaveDTO.setPassword("Password123!");
        testSaveDTO.setBudgetStartDay(1);
    }

    @Nested
    @DisplayName("saveAndUpdate - Create")
    class CreateTests {

        @Test
        @DisplayName("Should create user successfully")
        void shouldCreateUserSuccessfully() {
            when(userRepository.existsByEmailAndActiveTrue("test@ohchurus.com")).thenReturn(false);
            when(passwordEncoder.encode("Password123!")).thenReturn("encoded-password");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            assertNotNull(result.getObject());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should fail when email already exists")
        void shouldFailWhenEmailExists() {
            when(userRepository.existsByEmailAndActiveTrue("test@ohchurus.com")).thenReturn(true);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(102, result.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should fail when password is missing for new user")
        void shouldFailWhenPasswordMissing() {
            testSaveDTO.setPassword(null);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(101, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when password is blank for new user")
        void shouldFailWhenPasswordBlank() {
            testSaveDTO.setPassword("   ");

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(101, result.getErrorCode());
        }

        @Test
        @DisplayName("Should use default budgetStartDay when null")
        void shouldUseDefaultBudgetStartDay() {
            testSaveDTO.setBudgetStartDay(null);
            when(userRepository.existsByEmailAndActiveTrue(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(userRepository).save(argThat(user -> user.getBudgetStartDay() == 1));
        }

        @Test
        @DisplayName("Should accept budgetStartDay = 31")
        void shouldAcceptBudgetStartDay31() {
            testSaveDTO.setBudgetStartDay(31);
            when(userRepository.existsByEmailAndActiveTrue(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(userRepository).save(argThat(user -> user.getBudgetStartDay() == 31));
        }

        @Test
        @DisplayName("Should trim and save email with spaces correctly")
        void shouldCreateUserWithEmailContainingSpaces() {
            testSaveDTO.setEmail("  test@ohchurus.com  ");
            when(userRepository.existsByEmailAndActiveTrue("  test@ohchurus.com  ")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("saveAndUpdate - Update")
    class UpdateTests {

        @BeforeEach
        void setUp() {
            testSaveDTO.setId(1L);
        }

        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndActiveTrueAndIdNot("test@ohchurus.com", 1L)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Should fail when user not found")
        void shouldFailWhenUserNotFound() {
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.empty());

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(103, result.getErrorCode());
        }

        @Test
        @DisplayName("Should fail when email taken by another user")
        void shouldFailWhenEmailTakenByAnother() {
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndActiveTrueAndIdNot("test@ohchurus.com", 1L)).thenReturn(true);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertFalse(result.isCorrect());
            assertEquals(102, result.getErrorCode());
        }

        @Test
        @DisplayName("Should update without changing password when null")
        void shouldNotChangePasswordWhenNull() {
            testSaveDTO.setPassword(null);
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndActiveTrueAndIdNot(anyString(), anyLong())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            userService.saveAndUpdate(testSaveDTO);

            verify(passwordEncoder, never()).encode(anyString());
        }

        @Test
        @DisplayName("Should update password when provided")
        void shouldUpdatePasswordWhenProvided() {
            testSaveDTO.setPassword("NewPass123!");
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndActiveTrueAndIdNot(anyString(), anyLong())).thenReturn(false);
            when(passwordEncoder.encode("NewPass123!")).thenReturn("new-encoded");
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            userService.saveAndUpdate(testSaveDTO);

            verify(passwordEncoder).encode("NewPass123!");
        }

        @Test
        @DisplayName("Should update budgetStartDay to 31")
        void shouldUpdateBudgetStartDayTo31() {
            testSaveDTO.setBudgetStartDay(31);
            testSaveDTO.setPassword(null);
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndActiveTrueAndIdNot(anyString(), anyLong())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(userRepository).save(argThat(user -> user.getBudgetStartDay() == 31));
        }

        @Test
        @DisplayName("Should keep existing budgetStartDay when null in update")
        void shouldKeepExistingBudgetStartDayWhenNull() {
            testSaveDTO.setBudgetStartDay(null);
            testSaveDTO.setPassword(null);
            testUser.setBudgetStartDay(15);
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.existsByEmailAndActiveTrueAndIdNot(anyString(), anyLong())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toResultDTO(any(User.class))).thenReturn(testResultDTO);

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);

            assertTrue(result.isCorrect());
            verify(userRepository).save(argThat(user -> user.getBudgetStartDay() == 15));
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("Should return user when found")
        void shouldReturnUserWhenFound() {
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userMapper.toResultDTO(testUser)).thenReturn(testResultDTO);

            ResultDTO result = userService.getById(1L);

            assertTrue(result.isCorrect());
            assertNotNull(result.getObject());
        }

        @Test
        @DisplayName("Should return error when not found")
        void shouldReturnErrorWhenNotFound() {
            when(userRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = userService.getById(99L);

            assertFalse(result.isCorrect());
            assertEquals(103, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAllTests {

        @Test
        @DisplayName("Should return paginated list")
        void shouldReturnPaginatedList() {
            UserFilterDTO filter = new UserFilterDTO();
            filter.setPage(0);
            filter.setSize(10);

            Page<User> page = new PageImpl<>(List.of(testUser));
            when(userRepository.findAllWithFilters(any(), any(), any(Pageable.class))).thenReturn(page);
            when(userMapper.toResultDTO(testUser)).thenReturn(testResultDTO);

            ResultDTO result = userService.getAll(filter);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result.getObject();
            assertEquals(1, ((List<?>) response.get("list")).size());
            assertEquals(0, response.get("page"));
        }

        @Test
        @DisplayName("Should return empty list when no users")
        void shouldReturnEmptyList() {
            UserFilterDTO filter = new UserFilterDTO();
            Page<User> emptyPage = new PageImpl<>(List.of());
            when(userRepository.findAllWithFilters(any(), any(), any(Pageable.class))).thenReturn(emptyPage);

            ResultDTO result = userService.getAll(filter);

            assertTrue(result.isCorrect());
            @SuppressWarnings("unchecked")
            Map<String, Object> response = (Map<String, Object>) result.getObject();
            assertTrue(((List<?>) response.get("list")).isEmpty());
        }

        @Test
        @DisplayName("Should pass name filter to repository")
        void shouldPassNameFilterToRepository() {
            UserFilterDTO filter = new UserFilterDTO();
            filter.setName("Test");
            filter.setPage(0);
            filter.setSize(10);

            Page<User> page = new PageImpl<>(List.of(testUser));
            when(userRepository.findAllWithFilters(eq("Test"), any(), any(Pageable.class))).thenReturn(page);
            when(userMapper.toResultDTO(testUser)).thenReturn(testResultDTO);

            ResultDTO result = userService.getAll(filter);

            assertTrue(result.isCorrect());
            verify(userRepository).findAllWithFilters(eq("Test"), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("Should pass email filter to repository")
        void shouldPassEmailFilterToRepository() {
            UserFilterDTO filter = new UserFilterDTO();
            filter.setEmail("test@ohchurus.com");
            filter.setPage(0);
            filter.setSize(10);

            Page<User> page = new PageImpl<>(List.of(testUser));
            when(userRepository.findAllWithFilters(any(), eq("test@ohchurus.com"), any(Pageable.class))).thenReturn(page);
            when(userMapper.toResultDTO(testUser)).thenReturn(testResultDTO);

            ResultDTO result = userService.getAll(filter);

            assertTrue(result.isCorrect());
            verify(userRepository).findAllWithFilters(any(), eq("test@ohchurus.com"), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("Should soft delete user")
        void shouldSoftDeleteUser() {
            when(userRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            ResultDTO result = userService.delete(1L);

            assertTrue(result.isCorrect());
            verify(userRepository).save(argThat(user -> !user.getActive()));
        }

        @Test
        @DisplayName("Should fail when user not found for deletion")
        void shouldFailWhenUserNotFoundForDeletion() {
            when(userRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

            ResultDTO result = userService.delete(99L);

            assertFalse(result.isCorrect());
            assertEquals(103, result.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle exception in save gracefully")
        void shouldHandleExceptionInSave() {
            when(userRepository.existsByEmailAndActiveTrue(anyString()))
                    .thenThrow(new RuntimeException("DB error"));

            ResultDTO result = userService.saveAndUpdate(testSaveDTO);
            assertFalse(result.isCorrect());
            assertEquals(500, result.getErrorCode());
        }
    }
}
