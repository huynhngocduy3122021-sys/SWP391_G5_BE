// Lấy token từ localStorage
function getToken() {
  return localStorage.getItem("token");
}

// Thêm Authorization Header vào fetch config
function getAuthHeaders(headers = {}) {
  const token = getToken();
  return {
    ...headers,
    "Authorization": token ? "Bearer " + token : ""
  };
}

// Kiểm tra phản hồi có bị lỗi Auth không
async function handleResponse(response) {
  if (response.status === 401 || response.status === 403) {
    alert("Phiên làm việc hết hạn hoặc không có quyền. Vui lòng đăng nhập lại!");
    logout();
    throw new Error("Unauthorized");
  }
  if (!response.ok) {
    const errMsg = await response.text();
    throw new Error(errMsg || "Lỗi yêu cầu API");
  }
  return response;
}

// =========================================================================
// HÀM LOGIC ĐIỀU KIỆN 1: showRegister(show)
// Dùng để chuyển đổi qua lại giữa màn hình Đăng Nhập & Đăng Ký bên trong thẻ auth-card
// - show = true: Hiện màn hình Đăng Ký (#registerCard) & Ẩn Đăng Nhập (#loginCard)
// - show = false: Hiện màn hình Đăng Nhập (#loginCard) & Ẩn Đăng Ký (#registerCard)
// =========================================================================
function showRegister(show) {
  const tabLogin = document.getElementById("tabLogin");
  const tabRegister = document.getElementById("tabRegister");
  const loginCard = document.getElementById("loginCard");
  const registerCard = document.getElementById("registerCard");

  if (show) {
    loginCard.style.display = "none";
    registerCard.style.display = "block";
    tabRegister.classList.add("active");
    tabLogin.classList.remove("active");
  } else {
    loginCard.style.display = "block";
    registerCard.style.display = "none";
    tabLogin.classList.add("active");
    tabRegister.classList.remove("active");
  }
}

// Hàm toggle ẩn/hiện mật khẩu
function togglePasswordVisibility(inputId, toggleBtn) {
  const input = document.getElementById(inputId);
  const type = input.getAttribute("type") === "password" ? "text" : "password";
  input.setAttribute("type", type);
  
  const svg = toggleBtn.querySelector("svg");
  if (type === "text") {
    svg.innerHTML = `
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
      <line x1="1" y1="1" x2="23" y2="23"></line>
    `;
  } else {
    svg.innerHTML = `
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
      <circle cx="12" cy="12" r="3"></circle>
    `;
  }
}

// Thông báo các nút liên kết mạng xã hội
function socialLogin(platform) {
  alert("Tính năng đăng nhập bằng " + platform + " đang được phát triển!");
}

// Hàm Đăng ký tài khoản
async function register() {
  const req = {
    userFullName: document.getElementById("regName").value,
    userEmail: document.getElementById("regEmail").value,
    userPassword: document.getElementById("regPassword").value,
    userPhone: document.getElementById("regPhone").value,
    userAddress: document.getElementById("regAddress").value
  };

  if (!req.userFullName || !req.userEmail || !req.userPassword) {
    alert("Vui lòng điền các trường bắt buộc (Họ tên, Email, Mật khẩu)!");
    return;
  }

  try {
    const resp = await fetch("/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(req)
    });
    if (!resp.ok) {
      const err = await resp.text();
      throw new Error(err || "Lỗi đăng ký");
    }
    alert("Đăng ký tài khoản thành công! Hãy đăng nhập.");
    showRegister(false);
  } catch (error) {
    alert("Không thể đăng ký: " + error.message);
  }
}

// Hàm Đăng nhập
async function login() {
  const req = {
    userEmail: document.getElementById("loginEmail").value,
    userPassword: document.getElementById("loginPassword").value
  };

  if (!req.userEmail || !req.userPassword) {
    alert("Vui lòng điền Email và Mật khẩu!");
    return;
  }

  try {
    const resp = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(req)
    });
    if (!resp.ok) {
      const err = await resp.text();
      throw new Error(err || "Thông tin đăng nhập sai");
    }
    const data = await resp.json();
    localStorage.setItem("token", data.token);
    localStorage.setItem("email", data.userEmail);
    
    alert("Đăng nhập thành công!");
    checkAuthStatus();
  } catch (error) {
    alert("Đăng nhập thất bại: " + error.message);
  }
}

// Hàm Đăng xuất
function logout() {
  localStorage.removeItem("token");
  localStorage.removeItem("email");
  checkAuthStatus();
}

// =========================================================================
// HÀM LOGIC ĐIỀU KIỆN 2: checkAuthStatus()
// Dùng để kiểm tra token và chuyển đổi toàn bộ giao diện:
// - ĐÃ ĐĂNG NHẬP (Có token): Ẩn khối Auth (#authWrapper) & Hiện Dashboard (#mainDashboard)
// - CHƯA ĐĂNG NHẬP (Không token): Hiện khối Auth (#authWrapper) & Ẩn Dashboard (#mainDashboard)
// =========================================================================
function checkAuthStatus() {
  const token = getToken();
  const email = localStorage.getItem("email");
  
  const authWrapper = document.getElementById("authWrapper");
  const mainDashboard = document.getElementById("mainDashboard");
  const mainContent = document.getElementById("mainContent");
  const userInfo = document.getElementById("userInfo");
  const userEmailDisplay = document.getElementById("userEmailDisplay");

  if (token) {
    authWrapper.style.display = "none";
    mainDashboard.style.display = "block";
    mainContent.style.display = "block";
    userInfo.style.display = "flex";
    userEmailDisplay.innerText = email;
    document.body.classList.remove("auth-body");
    loadSlots();
  } else {
    authWrapper.style.display = "flex";
    mainDashboard.style.display = "none";
    mainContent.style.display = "none";
    userInfo.style.display = "none";
    document.body.classList.add("auth-body");
    showRegister(false);
  }
}

// Hàm tải lại danh sách các slot đỗ xe từ Backend API và hiển thị lên bảng HTML
async function loadSlots() {
  try {
    const resp = await fetch("/api/parking/slots", {
      headers: getAuthHeaders()
    });
    await handleResponse(resp);
    const slots = await resp.json();
    const tbody = document.getElementById("slotTableBody");
    tbody.innerHTML = "";
    
    slots.forEach((slot) => {
      const row = document.createElement("tr");
      row.innerHTML = `
        <td>${slot.id ?? ""}</td>
        <td>${slot.slotCode ?? ""}</td>
        <td>${slot.vehicleType ?? ""}</td>
        <td>${slot.available ? "Có sẵn" : "Không có"}</td>
        <td><button class="btn-delete" onclick="deleteSlot(${slot.id})">Xóa</button></td>
      `;
      tbody.appendChild(row);
    });
  } catch (error) {
    if (error.message !== "Unauthorized") {
      alert("Không tải được danh sách slot: " + error.message);
    }
  }
}

// Hàm tạo mới một slot đỗ xe bằng cách gọi POST API của backend
async function createSlot() {
  const slot = {
    slotCode: document.getElementById("newSlotCode").value,
    vehicleType: document.getElementById("newVehicleType").value,
    available: document.getElementById("newAvailable").value === "true",
  };
  try {
    const resp = await fetch("/api/parking/slots", {
      method: "POST",
      headers: getAuthHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(slot),
    });
    await handleResponse(resp);
    loadSlots();
    alert("Tạo slot thành công");
  } catch (error) {
    if (error.message !== "Unauthorized") {
      alert("Không thể tạo slot: " + error.message);
    }
  }
}

// Hàm cập nhật thông tin slot đỗ xe bằng cách gọi PUT API của backend
async function updateSlot() {
  const id = document.getElementById("updateSlotId").value;
  const slot = {
    slotCode: document.getElementById("updateSlotCode").value,
    vehicleType: document.getElementById("updateVehicleType").value,
    available:
      document.getElementById("updateAvailable").value === "true",
  };
  if (!id) {
    alert("Vui lòng nhập ID slot cần cập nhật");
    return;
  }
  try {
    const resp = await fetch("/api/parking/slots/" + id, {
      method: "PUT",
      headers: getAuthHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify(slot),
    });
    await handleResponse(resp);
    loadSlots();
    alert("Cập nhật thành công");
  } catch (error) {
    if (error.message !== "Unauthorized") {
      alert("Không thể cập nhật slot: " + error.message);
    }
  }
}

// Hàm xóa slot đỗ xe bằng cách gọi DELETE API của backend
async function deleteSlot(id) {
  if (!confirm("Xóa slot " + id + " ?")) return;
  try {
    const resp = await fetch("/api/parking/slots/" + id, {
      method: "DELETE",
      headers: getAuthHeaders()
    });
    await handleResponse(resp);
    loadSlots();
    alert("Xóa slot thành công");
  } catch (error) {
    if (error.message !== "Unauthorized") {
      alert("Không thể xóa slot: " + error.message);
    }
  }
}

// Đăng ký sự kiện: Khi trang web tải xong (load), kiểm tra trạng thái Auth
window.addEventListener("load", checkAuthStatus);
