import { useState } from 'react';
import { Search, StopCircle, Check, X } from 'lucide-react';
import './MasterAdmin.css';

interface Staff {
    id: number;
    name: string;
    phone: string;
    loginId: string;
    status: 'ACTIVE' | 'PENDING' | 'SUSPENDED';
}

export default function MasterAdmin() {
    const [staffList, setStaffList] = useState<Staff[]>([
        { id: 1, name: '김직원', phone: '010-1111-2222', loginId: 'staff1', status: 'ACTIVE' },
        { id: 2, name: '이신입', phone: '010-3333-4444', loginId: 'newbie', status: 'PENDING' },
        { id: 3, name: '박정지', phone: '010-5555-6666', loginId: 'baduser', status: 'SUSPENDED' },
    ]);

    const handleApprove = (id: number) => {
        if (window.confirm('이 직원의 등록을 승인하시겠습니까?')) {
            setStaffList(staffList.map(s => s.id === id ? { ...s, status: 'ACTIVE' } : s));
        }
    };

    const handleSuspend = (id: number) => {
        if (window.confirm('정말 이 직원의 기능을 정지하시겠습니까?')) {
            setStaffList(staffList.map(s => s.id === id ? { ...s, status: 'SUSPENDED' } : s));
        }
    };

    // Simple filter
    const activeStaff = staffList.filter(s => s.status === 'ACTIVE');
    const pendingStaff = staffList.filter(s => s.status === 'PENDING');

    return (
        <div className="master-container">
            <h2 className="page-title">마스터 관리</h2>

            <div className="section">
                <h3 className="section-title">승인 대기 요청 ({pendingStaff.length})</h3>
                {pendingStaff.length === 0 ? <div className="empty-state card">대기 중인 요청이 없습니다.</div> : (
                    <div className="card-grid">
                        {pendingStaff.map(staff => (
                            <div key={staff.id} className="staff-card card pending">
                                <div className="staff-header">
                                    <span className="staff-name">{staff.name}</span>
                                    <span className="staff-id">({staff.loginId})</span>
                                </div>
                                <div className="staff-body">
                                    <p>{staff.phone}</p>
                                    <p className="status-label">승인 대기중</p>
                                </div>
                                <div className="staff-actions">
                                    <button className="btn btn-primary btn-sm" onClick={() => handleApprove(staff.id)}>
                                        <Check size={16} /> 승인
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <div className="section">
                <h3 className="section-title">직원 목록 및 관리</h3>
                <div className="table-container card">
                    <table className="data-table">
                        <thead>
                            <tr>
                                <th>이름</th>
                                <th>아이디</th>
                                <th>전화번호</th>
                                <th>상태</th>
                                <th>관리</th>
                            </tr>
                        </thead>
                        <tbody>
                            {activeStaff.concat(staffList.filter(s => s.status === 'SUSPENDED')).map(staff => (
                                <tr key={staff.id}>
                                    <td>{staff.name}</td>
                                    <td>{staff.loginId}</td>
                                    <td>{staff.phone}</td>
                                    <td>
                                        <span className={`status-badge-small ${staff.status.toLowerCase()}`}>
                                            {staff.status}
                                        </span>
                                    </td>
                                    <td>
                                        {staff.status === 'ACTIVE' && (
                                            <button className="btn btn-danger btn-sm" onClick={() => handleSuspend(staff.id)}>
                                                <StopCircle size={16} /> 정지
                                            </button>
                                        )}
                                        {staff.status === 'SUSPENDED' && (
                                            <button className="btn btn-outline btn-sm" onClick={() => handleApprove(staff.id)}>
                                                해제
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
