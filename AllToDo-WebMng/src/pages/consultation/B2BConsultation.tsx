import React, { useState } from 'react';
import { Search, Mail, MessageSquare, FileText, CheckCircle, XCircle } from 'lucide-react';
import './Consultation.css';

interface B2BUser {
    id: number;
    companyName: string;
    managerName: string;
    phone: string;
    joinDate: string;
    services: string[];
    paymentRegistered: boolean;
    history: { date: string; type: string; amount: number; status: string }[];
}

export default function B2BConsultation() {
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedUser, setSelectedUser] = useState<B2BUser | null>(null);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        // MOck Data
        if (searchTerm === '010-1234-5678') {
            setSelectedUser({
                id: 1,
                companyName: '(주)올투두',
                managerName: '김철수',
                phone: '010-1234-5678',
                joinDate: '2025-01-15',
                services: ['출퇴근', '업무지시', '택시'],
                paymentRegistered: true,
                history: [
                    { date: '2025-03-01', type: '정기결제', amount: 50000, status: 'Completed' },
                    { date: '2025-02-01', type: '정기결제', amount: 50000, status: 'Completed' }
                ]
            });
        } else {
            alert('검색 결과가 없습니다. (Hint: 010-1234-5678)');
            setSelectedUser(null);
        }
    };

    const handleSendBill = (method: 'sms' | 'email') => {
        alert(`${method === 'sms' ? '문자' : '이메일'}로 고지서를 발송했습니다.`);
    };

    return (
        <div className="consultation-container">
            <h2 className="page-title">B2B 상담</h2>

            <div className="search-section card">
                <form onSubmit={handleSearch} className="search-form">
                    <input
                        type="text"
                        className="input-field search-input"
                        placeholder="가입자 전화번호 검색"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                    <button type="submit" className="btn btn-primary search-btn">
                        <Search size={20} /> 검색
                    </button>
                </form>
            </div>

            {selectedUser && (
                <div className="result-section">
                    <div className="user-info-card card">
                        <div className="card-header">
                            <h3>기본 정보</h3>
                            <span className="join-date">가입일: {selectedUser.joinDate}</span>
                        </div>
                        <div className="info-grid">
                            <div className="info-item">
                                <label>회사명</label>
                                <span>{selectedUser.companyName || '-'}</span>
                            </div>
                            <div className="info-item">
                                <label>담당자</label>
                                <span>{selectedUser.managerName}</span>
                            </div>
                            <div className="info-item">
                                <label>전화번호</label>
                                <span>{selectedUser.phone}</span>
                            </div>
                        </div>

                        <div className="services-section">
                            <label>사용 서비스</label>
                            <div className="chips">
                                {selectedUser.services.map(s => <span key={s} className="chip">{s}</span>)}
                            </div>
                        </div>

                        <div className="payment-status">
                            <label>결제 정보 등록 여부</label>
                            <div className="status-badge">
                                {selectedUser.paymentRegistered ?
                                    <><CheckCircle size={16} color="green" /> <span>등록됨 (Master만 상세 조회 가능)</span></> :
                                    <><XCircle size={16} color="red" /> <span>미등록</span></>
                                }
                            </div>
                        </div>

                        <div className="actions">
                            <button className="btn btn-outline" onClick={() => handleSendBill('sms')}>
                                <MessageSquare size={16} /> 고지서 문자 발송
                            </button>
                            <button className="btn btn-outline" onClick={() => handleSendBill('email')}>
                                <Mail size={16} /> 고지서 메일 발송
                            </button>
                        </div>
                    </div>

                    <div className="history-card card">
                        <h3>결제/미납 내역</h3>
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>날짜</th>
                                    <th>유형</th>
                                    <th>금액</th>
                                    <th>상태</th>
                                </tr>
                            </thead>
                            <tbody>
                                {selectedUser.history.map((h, i) => (
                                    <tr key={i}>
                                        <td>{h.date}</td>
                                        <td>{h.type}</td>
                                        <td>{h.amount.toLocaleString()}원</td>
                                        <td><span className={`status-text ${h.status.toLowerCase()}`}>{h.status}</span></td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}
