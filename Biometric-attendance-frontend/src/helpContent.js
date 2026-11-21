/*
  Help Center text. Everything here describes what the system really does.
  Contact details are NOT written here: they are loaded from the server
  (Settings > Support Phone, or the administrator's profile) so they are always the
  school's real details and are never invented.
*/

export const EMPLOYEE_HELP = {
  title: "Employee Help Center",
  intro:
    "Step-by-step help for using the attendance system. Use the search box or open a topic.",
  topics: [
    {
      id: "signin",
      icon: "🔐",
      title: "Signing in and out of the system",
      steps: [
        "Open the school attendance page. It opens straight on the employee login.",
        "On the Sign In tab enter your Employee ID or username and your password, then press Login.",
        "To leave, press Log out in the sidebar. Always log out on a shared computer.",
      ],
      notes: [
        "After 5 wrong passwords the account is paused for 15 minutes. Use Reset Password if you forgot it.",
        "If your password is changed or reset, every browser you were signed in on is signed out.",
      ],
    },
    {
      id: "attendance",
      icon: "🕘",
      title: "Marking attendance (sign in / sign out)",
      steps: [
        "Open Mark attendance and allow the browser to use your location.",
        "You must be inside the school area. The page shows your distance from the school.",
        "Press Sign In, then confirm with your registered device (fingerprint, Face unlock or screen lock).",
        "At the end of the day press Sign Out and confirm again.",
      ],
      notes: [
        "Sign in after the official arrival time is recorded as Late.",
        "You can sign in once and sign out once each day.",
        "Attendance is recorded ONLY with the fingerprint (or Face / screen lock) of your registered device. There is no password option. If you cannot use your device, contact the school administrator.",
      ],
    },
    {
      id: "device",
      icon: "📱",
      title: "Your attendance device (one device per account)",
      steps: [
        "Open Setting > Attendance Device, type a Device Name (for example My Tecno Phone) and press Register this device.",
        "Confirm with the fingerprint, Face unlock or screen lock of that device.",
        "Each account can have only ONE registered device. Trying to register another shows: \"This account's device fingerprint is already registered.\"",
        "New phone, damaged device or fingerprint problems: press Request device removal, choose a reason and confirm with your fingerprint. The request is PENDING until the administrator approves or rejects it; you will see the result under Notifications.",
        "Phone stolen or lost: press \"My phone was stolen / lost\". The administrator is told at once and can revoke the phone immediately. To register a new device the administrator must first confirm your identity and give you a verification code, which you enter in the same place.",
        "You can never remove the device yourself. After approval, register the new phone in Setting > Attendance Device.",
      ],
      notes: [
        "Your fingerprint image is never stored. Only a cryptographic key that stays tied to your device is used.",
        "The Device Name you type is shown under 'Registered device' with its status (ACTIVE / REVOKED), registration date and last used time. The name is for display only and plays no part in security.",
      ],
    },
    {
      id: "history",
      icon: "📊",
      title: "Attendance history and the weekly report",
      steps: [
        "Open Attendance report to see your summary (present days, late days, total hours) and your Monday-Friday week.",
        "The 'Summary weekly report' chart shows the hours you worked each day. It opens on Current Week; press Previous Week to see the week before.",
        "If there are no records for the previous week, the chart says so instead of showing numbers.",
      ],
      notes: [
        "Weeks run Monday to Sunday in the school's time zone. Upcoming days are not counted as absences.",
        "You only ever see your own records.",
      ],
    },
    {
      id: "profile",
      icon: "👤",
      title: "Profile and photo",
      steps: [
        "Your profile shows your full name (first, middle and last), Employee ID, department and contact details. These are set by the administrator.",
        "Press Change photo on your dashboard and choose a JPG, PNG or GIF image of 2 MB or less, with a blue background.",
        "Your photo is then also shown to the administrator in the employee list.",
      ],
      notes: ["If a detail in your profile is wrong, ask the administrator to correct it."],
    },
    {
      id: "password",
      icon: "🔑",
      title: "Changing or resetting your password",
      steps: [
        "To change it while signed in: Setting > Change Password. Enter your current password and the new one twice.",
        "Forgot it? On the login page choose the Reset Password tab, enter your Employee ID/username and the e-mail registered on your account.",
        "Open the e-mail you receive and press the link. It works once and expires after a short time.",
        "Choose a new password and confirm it, then sign in with it.",
      ],
      notes: [
        "A password needs at least 8 characters with at least one letter and one number.",
        "For safety the page gives the same answer whether or not the details match an account. If no e-mail arrives, check spam, or ask the administrator to confirm the e-mail on your account or set a new password for you.",
        "Nobody - not even the administrator - can see your password.",
      ],
    },
    {
      id: "errors",
      icon: "⚠️",
      title: "Common problems",
      table: [
        ["You are outside the allowed school area", "Move inside the school grounds, wait for a better GPS signal, then try again."],
        ["Location permission denied", "Allow Location for this site in the browser settings and reload."],
        ["This account's device fingerprint is already registered.", "Your account already has a device. Use Request device removal in Setting > Attendance Device."],
        ["The Employee ID or activation code is not correct", "Check the Employee ID and the code the administrator gave you. Codes work once and expire after 7 days; ask the administrator for a new one."],
        ["Biometric needs HTTPS / not supported", "Use the school's secure (https) address and a phone/computer with a fingerprint, Face unlock or screen lock set up."],
        ["Too many failed attempts", "Wait 15 minutes, or use Reset Password."],
        ["Your password was changed. Please sign in again.", "Your password was changed on another browser. Sign in with the new password."],
        ["You have already signed in / out today", "Only one sign in and one sign out are allowed per day."],
        ["Cannot reach the server", "Check your internet connection and try again. If it continues, contact support."],
      ],
    },
  ],
};

export const ADMIN_HELP = {
  title: "Administrator Help Center",
  intro:
    "Guidance for managing employees, attendance and settings. Use the search box or open a topic.",
  topics: [
    {
      id: "signin",
      icon: "🔐",
      title: "Signing in and out",
      steps: [
        "Open the administrator address of the system (the main address followed by /admin), enter your username and password and press Login as Administrator.",
        "Use Logout at the bottom of the sidebar when you finish.",
      ],
      notes: [
        "Your username can be changed in Settings > Administrator Profile. It must be unique; the new username works at the next login.",
        "Forgot the password? Use the Reset Password tab with your username and the e-mail saved in your profile.",
      ],
    },
    {
      id: "employees",
      icon: "👤",
      title: "Registering and managing employees",
      steps: [
        "Open Manage Employee. Enter Employee ID, First name, optional Middle name, Last name, e-mail, phone, department, position and hire date. There is no username or password field: after saving, the page shows a one-time ACTIVATION CODE. Give it to the employee; they choose Register on the login page and create their own username and password.",
        "There is no photo field: each employee uploads their own photo from their dashboard, and it appears in the directory.",
        "Use View, Edit, Activate/Deactivate or Delete in the directory. Deactivated employees cannot sign in.",
        "If an employee lost their activation code (or it expired), press Activation code on that employee for a new one. For an employee who already has an account, leave the password empty when editing to keep the current one; typing a new one signs the employee out everywhere.",
      ],
      notes: [
        "Employee ID cannot be changed after registration.",
        "Passwords are stored as one-way hashes. You cannot view an employee's password; you can only set a new one.",
        "Deleting an employee also deletes their attendance history. Deactivate instead if you want to keep records.",
      ],
    },
    {
      id: "devices",
      icon: "📱",
      title: "Employee devices",
      steps: [
        "Open Device Management in the menu. The red number shows pending removal requests; EMERGENCY reports (stolen / lost phones) are listed first.",
        "Normal request: press Approve (the old device is revoked and the employee is notified) or Reject and write the reason the employee will see.",
        "Emergency report: confirm the employee's identity by phone or in person, press Revoke immediately, then give the employee the verification code shown once. They need it to register a new device.",
        "The Devices tab lists every device with its status and last used time, and keeps revoked devices as history. History / audit shows who did what and when.",
        "Reset device in Manage Employee still removes a device directly (use it only when you are sure of the employee).",
      ],
      notes: [
        "Reset a device only after you are sure the request comes from the employee.",
        "Device names come from the browser and are informational; they are not proof of identity.",
      ],
    },
    {
      id: "attendance",
      icon: "👥",
      title: "Viewing attendance",
      steps: [
        "Open View Employees Attendance. It opens on Today (the school's date). Use the Attendance Period list for All Records, This Week or This Month.",
        "Press View Location on a row, or Show map of these records, to see where sign-ins happened on the map.",
      ],
      notes: [
        "On the map the school is the blue marker with the allowed-radius circle; employee markers are drawn only from locations the server really recorded.",
      ],
    },
    {
      id: "comparison",
      icon: "📈",
      title: "Overall Employees Weekly Report",
      steps: [
        "View Employees Attendance shows the 'Overall Employees Weekly Report': present, late and absent employees for each day from Monday to Friday.",
        "It opens on Current Week; press Previous Week to see the week before. If the previous week has no records, a message is shown instead of the chart.",
      ],
      notes: ["Weeks run Monday to Sunday. Future days are not counted as absent."],
    },
    {
      id: "settings",
      icon: "⚙️",
      title: "Settings: profile, work hours, location, support contact",
      steps: [
        "Administrator Profile: name, username, e-mail, phone, photo.",
        "Work time rules: official arrival (after it a sign in is Late) and departure.",
        "Attendance location: school latitude, longitude and allowed radius. Employees outside the radius cannot mark attendance.",
        "Support Phone: the number shown to employees in the Help Center. If empty, your own phone number is shown.",
        "Save with the Save button at the bottom of the page.",
      ],
      notes: ["Keep your e-mail correct: it is where password reset links are sent."],
    },
    {
      id: "password",
      icon: "🔑",
      title: "Passwords and resets",
      steps: [
        "Change your own password in Settings > Security. Policy: at least 8 characters with a letter and a number.",
        "Reset links are sent only to the e-mail saved on the account, work once and expire.",
        "If an employee has no e-mail or cannot receive it, edit the employee and type a new password for them.",
      ],
      notes: ["The server needs mail (SMTP) settings to send reset e-mails. See the deployment notes."],
    },
    {
      id: "errors",
      icon: "⚠️",
      title: "Common problems",
      table: [
        ["Username already taken", "Choose another username; usernames are unique."],
        ["Employee ID / e-mail already exists", "Each ID, username and e-mail can belong to one employee only."],
        ["Map shows no markers", "The selected records have no valid saved coordinates, or none match the period."],
        ["Reset e-mail not arriving", "Check the e-mail address on the account and the server's mail settings."],
        ["Session expired / signed out", "A password change or restart ends old sessions. Sign in again."],
      ],
    },
  ],
};
