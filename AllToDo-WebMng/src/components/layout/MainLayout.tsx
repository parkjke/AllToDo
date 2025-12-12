import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { Home, Users, Building2, ShieldCheck, LogOut, Phone } from 'lucide-react';
import './MainLayout.css';

export default function MainLayout() {
    const navigate = useNavigate();
    // Mock login check or role
    const role = localStorage.getItem('role') || 'staff';

    const handleLogout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('role');
        navigate('/login');
    };

    return (
        <div className="layout-container">
            <aside className="sidebar">
                <div className="sidebar-header">
                    <div className="logo-placeholder">AllToDo</div>
                    <span className="role-badge">{role.toUpperCase()}</span>
                </div>

                <nav className="nav-menu">
                    <NavLink to="/dashboard" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
                        <Home size={20} />
                        <span>Dashboard</span>
                    </NavLink>

                    <div className="nav-group-label">CONSULTATION</div>

                    <NavLink to="/consultation/b2b" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
                        <Building2 size={20} />
                        <span>B2B 상담</span>
                    </NavLink>

                    <NavLink to="/consultation/user" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
                        <Phone size={20} />
                        <span>사용자 상담</span>
                    </NavLink>

                    {role === 'master' && (
                        <>
                            <div className="nav-group-label">MASTER</div>
                            <NavLink to="/master" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
                                <ShieldCheck size={20} />
                                <span>마스터 관리</span>
                            </NavLink>
                        </>
                    )}
                </nav>

                <div className="sidebar-footer">
                    <div className="user-info">
                        <div className="avatar">AD</div>
                        <div className="info">
                            <span className="name">Admin User</span>
                            <span className="link" onClick={() => navigate('/profile')}>Edit Profile</span>
                        </div>
                    </div>
                    <button onClick={handleLogout} className="logout-btn">
                        <LogOut size={18} />
                    </button>
                </div>
            </aside>

            <main className="main-content">
                <Outlet />
            </main>
        </div>
    );
}
