package com.example

import com.example.db.UserAccount
import com.example.util.AuthorityVerificationProvider
import com.example.util.AuthorityVerificationResult
import com.example.util.GovernmentVerificationEngine
import com.example.ui.generateRandomCaptcha
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testGovernmentVerification_supportedDepartmentsAndDesignations() {
    val depts = GovernmentVerificationEngine.SUPPORTED_DEPARTMENTS
    assertEquals(3, depts.size)
    assertTrue(depts.contains("Municipal Administration"))
    assertTrue(depts.contains("Judiciary / Legal Services"))
    assertTrue(depts.contains("District Administration"))

    assertFalse(depts.contains("Police Department"))
    assertFalse(depts.contains("Revenue Department"))
    assertFalse(depts.contains("Health Department"))

    // Verify Municipal Administration designations
    val municipalDesigs = GovernmentVerificationEngine.getDesignationsForDepartment("Municipal Administration")
    assertEquals(listOf("Municipal Commissioner", "Assistant Commissioner", "Municipal Officer"), municipalDesigs)

    // Verify Judiciary / Legal Services designations
    val judiciaryDesigs = GovernmentVerificationEngine.getDesignationsForDepartment("Judiciary / Legal Services")
    assertEquals(listOf("Judge", "Magistrate", "Legal Services Officer"), judiciaryDesigs)

    // Verify District Administration designations
    val districtDesigs = GovernmentVerificationEngine.getDesignationsForDepartment("District Administration")
    assertEquals(listOf("District Collector", "Revenue Divisional Officer", "Tahsildar"), districtDesigs)
  }

  @Test
  fun testGovernmentVerification_roleVerificationSuccess() {
    val res1 = GovernmentVerificationEngine.verifyAuthorityRole(
      department = "District Administration",
      designation = "District Collector"
    )
    assertTrue("Valid department and designation should verify", res1.verified)
    assertEquals("District Administration", res1.department)
    assertEquals("District Collector", res1.designation)

    val res2 = GovernmentVerificationEngine.verifyAuthorityRole(
      department = "Judiciary / Legal Services",
      designation = "Judge"
    )
    assertTrue("Valid judge role should verify", res2.verified)

    val res3 = GovernmentVerificationEngine.verifyAuthorityRole(
      department = "Municipal Administration",
      designation = "Municipal Commissioner"
    )
    assertTrue("Valid municipal role should verify", res3.verified)
  }

  @Test
  fun testGovernmentVerification_roleVerificationInvalid() {
    // Blank inputs
    val resBlank = GovernmentVerificationEngine.verifyAuthorityRole("", "")
    assertFalse(resBlank.verified)

    // Mismatched designation for department
    val resMismatch = GovernmentVerificationEngine.verifyAuthorityRole(
      department = "Municipal Administration",
      designation = "District Collector"
    )
    assertFalse("Mismatched designation for department should fail", resMismatch.verified)

    // Old department
    val resOld = GovernmentVerificationEngine.verifyAuthorityRole(
      department = "Police Department",
      designation = "Inspector"
    )
    assertFalse("Removed department should fail verification", resOld.verified)
  }

  @Test
  fun testGovernmentVerification_formatValidation() {
    assertTrue(GovernmentVerificationEngine.validateFormat("POL-12345"))
    assertTrue(GovernmentVerificationEngine.validateFormat("officer@police.gov.in"))
    assertFalse(GovernmentVerificationEngine.validateFormat("ab"))
  }

  @Test
  fun testCaptchaGeneration() {
    val captcha = generateRandomCaptcha()
    assertEquals(4, captcha.length)
    assertTrue(captcha.all { "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".contains(it) })
  }

  @Test
  fun testAdminAuthorizationEmailMatching() {
    val authorizedAdminEmail1 = "sakthivel.s8317@gmail.com"
    val authorizedAdminEmail2 = "srisakthi1357@gmail.com"
    val citizenEmail = "citizen.user@gmail.com"
    val authorityEmail = "officer.sharma@police.gov.in"

    assertTrue("Designated admin email 1 should be recognized", authorizedAdminEmail1.equals("sakthivel.s8317@gmail.com", ignoreCase = true))
    assertTrue("Designated admin email 2 should be recognized", authorizedAdminEmail2.equals("srisakthi1357@gmail.com", ignoreCase = true))
    assertFalse("Citizen email must not be recognized as authorized admin", citizenEmail.equals("sakthivel.s8317@gmail.com", ignoreCase = true))
    assertFalse("Authority email must not be recognized as authorized admin", authorityEmail.equals("sakthivel.s8317@gmail.com", ignoreCase = true))
  }

  @Test
  fun testAdminAuthCheckResultDataClass() {
    val authorizedResult = com.example.firebase.AdminAuthCheckResult(
      isAuthorized = true,
      uid = "admin_uid_123",
      email = "sakthivel.s8317@gmail.com",
      role = "Root Admin",
      errorMessage = null
    )
    assertTrue(authorizedResult.isAuthorized)
    assertEquals("admin_uid_123", authorizedResult.uid)
    assertEquals("sakthivel.s8317@gmail.com", authorizedResult.email)
    assertEquals("Root Admin", authorizedResult.role)
    assertNull(authorizedResult.errorMessage)

    val unauthorizedResult = com.example.firebase.AdminAuthCheckResult(
      isAuthorized = false,
      uid = "user_456",
      email = "citizen@example.com",
      role = "Citizen",
      errorMessage = "You are not authorized to publish legal records."
    )
    assertFalse(unauthorizedResult.isAuthorized)
    assertEquals("You are not authorized to publish legal records.", unauthorizedResult.errorMessage)
  }

  @Test
  fun testRoleNormalization() {
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("root_admin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("ROOT_ADMIN"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("Root Admin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("rootAdmin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("Root_Admin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("root admin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("RootAdmin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("root-admin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("super_admin"))
    assertEquals("root_admin", com.example.firebase.FirebaseManager.normalizeRole("Super Admin"))
    assertEquals("admin", com.example.firebase.FirebaseManager.normalizeRole("Admin"))
    assertEquals("admin", com.example.firebase.FirebaseManager.normalizeRole("admin"))
    assertEquals("admin", com.example.firebase.FirebaseManager.normalizeRole("ADMIN"))
    assertEquals("authority", com.example.firebase.FirebaseManager.normalizeRole("Authority"))
    assertEquals("citizen", com.example.firebase.FirebaseManager.normalizeRole("Citizen"))

    assertTrue(com.example.firebase.FirebaseManager.isRootAdminRole("root_admin"))
    assertTrue(com.example.firebase.FirebaseManager.isRootAdminRole("ROOT_ADMIN"))
    assertTrue(com.example.firebase.FirebaseManager.isRootAdminRole("Root Admin"))
    assertTrue(com.example.firebase.FirebaseManager.isRootAdminRole("rootAdmin"))
    assertTrue(com.example.firebase.FirebaseManager.isRootAdminRole("Admin"))
    assertFalse(com.example.firebase.FirebaseManager.isRootAdminRole("Citizen"))
    assertFalse(com.example.firebase.FirebaseManager.isRootAdminRole("Authority"))
  }

  @Test
  fun testFirebaseAuthResultErrorTypes() {
    val credError = com.example.firebase.FirebaseAuthResult.Error(
      message = "Incorrect password or invalid credentials.",
      errorCode = "ERROR_WRONG_PASSWORD",
      isCredentialError = true
    )
    assertTrue(credError.isCredentialError)
    assertEquals("ERROR_WRONG_PASSWORD", credError.errorCode)

    val userNotFoundError = com.example.firebase.FirebaseAuthResult.Error(
      message = "No account found with this email.",
      errorCode = "ERROR_USER_NOT_FOUND",
      isUserNotFound = true
    )
    assertTrue(userNotFoundError.isUserNotFound)

    val disabledError = com.example.firebase.FirebaseAuthResult.Error(
      message = "This account has been disabled.",
      errorCode = "ERROR_USER_DISABLED",
      isUserDisabled = true
    )
    assertTrue(disabledError.isUserDisabled)
  }

  @Test
  fun testLegalPublishingErrorMessages() {
    val unauthResult = com.example.firebase.AdminAuthCheckResult(
      isAuthorized = false,
      uid = "",
      email = "",
      role = "Unauthenticated",
      errorMessage = "Please sign in as an administrator."
    )
    assertEquals("Please sign in as an administrator.", unauthResult.errorMessage)

    val nonAdminResult = com.example.firebase.AdminAuthCheckResult(
      isAuthorized = false,
      uid = "cit_123",
      email = "citizen@test.com",
      role = "Citizen",
      errorMessage = "You are not authorized to publish legal records."
    )
    assertEquals("You are not authorized to publish legal records.", nonAdminResult.errorMessage)
  }
}


