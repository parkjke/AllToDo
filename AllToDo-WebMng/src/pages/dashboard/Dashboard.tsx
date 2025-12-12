import { useNavigate } from 'react-router-dom';
import { Users, Building2, ShieldCheck, ArrowRight } from 'lucide-react';
import './Dashboard.css';

export default function Dashboard() {
    const navigate = useNavigate();
    const role = localStorage.getItem('role') || 'staff';

    return (
        <div className="dashboard-container">
            <h2 className="page-title">Dashboard</h2>

            <div className="stats-grid">
                <div className="card stat-card" onClick={() => navigate('/consultation/user')}>
                    <div className="stat-icon user-bg"><Users size={24} /></div>
                    <div className="stat-info">
                        <span className="stat-label">사용자 상담</span>
                        <span className="stat-value">바로가기</span>
                    </div>
                    <ArrowRight className="arrow-icon" size={20} />
                </div>

                <div className="card stat-card" onClick={() => navigate('/consultation/b2b')}>
                    <div className="stat-icon b2b-bg"><Building2 size={24} /></div>
                    <div className="stat-info">
                        <span className="stat-label">B2B 상담</span>
                        <span className="stat-value">바로가기</span>
                    </div>
                    <ArrowRight className="arrow-icon" size={20} />
                </div>

                {role === 'master' && (
                    <div className="card stat-card" onClick={() => navigate('/master')}>
                        <div className="stat-icon master-bg"><ShieldCheck size={24} /></div>
                        <div className="stat-info">
                            <span className="stat-label">마스터 관리</span>
                            <span className="stat-value">3건 승인 대기</span>
                        </div>
                        <ArrowRight className="arrow-icon" size={20} />
                    </div>
                )}
            </div>

            <div className="recent-activity card">
                <h3>최근 접속 로그</h3>
                <ul className="activity-list">
                    <li>
                        <span className="time">14:50</span>
                        <span className="desc">Admin User logged in</span>
                    </li>
                    <li>
                        <span className="time">10:20</span>
                        <span className="desc">Staff User processed B2B request</span>
                    </li>
                </ul>
            </div>
        </div>
    );
}
