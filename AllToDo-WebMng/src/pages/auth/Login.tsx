import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import './Auth.css';

export default function Login() {
    const navigate = useNavigate();
    const [formData, setFormData] = useState({ id: '', password: '' });
    const [error, setError] = useState('');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!formData.id || !formData.password) {
            setError('ID와 비밀번호를 입력해주세요.');
            return;
        }

        // Mock Login Logic
        if (formData.id === 'admin' && formData.password === '1234') {
            localStorage.setItem('role', 'master');
            localStorage.setItem('token', 'mock-master-token');
            navigate('/dashboard');
        } else if (formData.id === 'staff' && formData.password === '1234') {
            localStorage.setItem('role', 'staff');
            localStorage.setItem('token', 'mock-staff-token');
            navigate('/dashboard');
        } else {
            setError('아이디 또는 비밀번호가 올바르지 않습니다.');
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-card card">
                <h1 className="auth-title">AllToDo Mng</h1>
                <p className="auth-subtitle">관리자 / 직원 전용 시스템</p>

                <form onSubmit={handleSubmit} className="auth-form">
                    <div className="form-group">
                        <label>아이디</label>
                        <input
                            type="text"
                            className="input-field"
                            placeholder="ID를 입력하세요"
                            value={formData.id}
                            onChange={(e) => setFormData({ ...formData, id: e.target.value })}
                        />
                    </div>

                    <div className="form-group">
                        <label>비밀번호</label>
                        <input
                            type="password"
                            className="input-field"
                            placeholder="비밀번호"
                            value={formData.password}
                            onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                        />
                    </div>

                    {error && <div className="error-msg">{error}</div>}

                    <button type="submit" className="btn btn-primary btn-block">로그인</button>
                </form>

                <div className="auth-footer">
                    <Link to="/register">직원 등록 신청</Link>
                </div>
            </div>
        </div>
    );
}
