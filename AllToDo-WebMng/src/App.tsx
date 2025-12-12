import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './components/layout/MainLayout';
import Login from './pages/auth/Login';
import Register from './pages/auth/Register';
// Placeholders
const Dashboard = () => <h2>Dashboard</h2>;
const B2BConsultation = () => <h2>B2B Consultation</h2>;
const UserConsultation = () => <h2>User Consultation</h2>;
const MasterAdmin = () => <h2>Master Admin</h2>;

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        <Route path="/" element={<MainLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />

          <Route path="consultation">
            <Route path="b2b" element={<B2BConsultation />} />
            <Route path="user" element={<UserConsultation />} />
          </Route>

          <Route path="master" element={<MasterAdmin />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
